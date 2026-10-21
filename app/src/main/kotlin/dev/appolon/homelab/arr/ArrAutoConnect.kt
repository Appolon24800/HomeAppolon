package dev.appolon.homelab.arr

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.net.ServerEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Auto-provisions an *arr server from a plain dashboard link.
 *
 * The user's services sit behind SSO, and the app's WebView already holds
 * that session. So instead of asking for the API key, this loads the link
 * in a headless WebView that shares those cookies; once the *arr web app
 * renders, its own bootstrap global (`window.Radarr.apikey` and siblings)
 * hands us the key. Probing is origin-gated: only the service's own
 * origin is ever asked for its key, never the SSO pages in between. We
 * verify the key against the server's status endpoint, store the server,
 * and hand back its id: the native engine opens with zero manual setup.
 *
 * When extraction fails (session expired, unsupported build), the caller
 * falls back to the native setup sheet prefilled with URL and type; the
 * WebView is never silently substituted for the native UI.
 */
object ArrAutoConnect {

    sealed interface Outcome {
        /** A server now exists for the link; open the native engine. */
        data class Connected(val serverId: ServerId) : Outcome

        /** The key could not be recovered automatically; open native setup. */
        data object NeedSetup : Outcome
    }

    /** The WebView's Cookie header for [url], or null when there is none. */
    private fun webSessionCookie(url: String): String? = runCatching {
        android.webkit.CookieManager.getInstance().getCookie(url)
    }.getOrNull()

    /** Wall-clock budget for the whole invisible round trip. */
    private val TIMEOUT_MS = 15_000L

    /** Retry budget while the SPA bootstraps on a same-origin page. */
    private const val MAX_PROBES = 12
    private const val PROBE_RETRY_MS = 700L

    /** Status endpoint candidates across the supported app generations. */
    private val STATUS_PATHS = listOf(
        "/api/v3/system/status",
        "/api/v1/system/status",
        "/api/system/status",
    )

    /**
     * Resolves [serviceUrl] to a configured server, creating one when the
     * key extraction succeeds. Runs the WebView on the main thread; safe
     * to call from any coroutine scope.
     */
    suspend fun connect(context: Context, serviceUrl: String, appType: AppType): Outcome {
        val key = withTimeoutOrNull(TIMEOUT_MS) {
            extractApiKey(context.applicationContext, serviceUrl, appType)
        } ?: return Outcome.NeedSetup
        if (key.isNullOrBlank() || key.length < 16) return Outcome.NeedSetup
        val id = provision(context, appType, ServerEndpoint.normalizeBaseUrl(serviceUrl), key)
            ?: return Outcome.NeedSetup
        return Outcome.Connected(id)
    }

    /**
     * Verifies a known key against the server and persists the server.
     * Returns its id, or null when verification fails. Used by both the
     * WebView extraction and direct key links.
     */
    suspend fun provision(
        context: Context,
        appType: AppType,
        baseUrl: String,
        apiKey: String,
    ): ServerId? = withContext(Dispatchers.IO) {
        if (!verify(baseUrl, apiKey, appType)) return@withContext null
        runCatching {
            val runtime = ArrBootstrap.runtime(context.applicationContext)
            val graph = runtime.graph
            val existing = graph.registry.servers().firstOrNull {
                it.type == appType && it.baseUrl.trimEnd('/') == baseUrl.trimEnd('/')
            }
            val server = graph.registry.upsert(
                existing?.copy(apiKeyEncrypted = graph.secrets.encrypt(apiKey))
                    ?: dev.appolon.homelab.arr.data.ArrServer(
                        id = ServerId(java.util.UUID.randomUUID().toString()),
                        type = appType,
                        name = appType.displayName,
                        baseUrl = baseUrl,
                        apiKeyEncrypted = graph.secrets.encrypt(apiKey),
                    ),
            )
            server.id
        }.getOrElse { e ->
            // Keystore or registry failures (keychain unavailable, disk
            // full) read as "could not connect"; cancellation still
            // propagates so the caller's cleanup runs.
            if (e is CancellationException) throw e
            null
        }
    }

    /** Loads the page invisibly and returns the apikey its UI exposes. */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun extractApiKey(
        context: Context,
        url: String,
        appType: AppType,
    ): String? = withContext(Dispatchers.Main) {
        val webView = WebView(context)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        CookieManager.getInstance().let { it.setAcceptCookie(true); it.acceptThirdPartyCookies(webView) }

        // The WebView never attaches to a window, so View.post and
        // postDelayed enqueue onto a run queue that only drains during a
        // layout pass; for a detached view that may never happen. The
        // main looper always runs.
        val handler = Handler(Looper.getMainLooper())
        var settled = false
        var destroyed = false
        var probeCount = 0
        fun destroyWebView() {
            if (destroyed) return
            destroyed = true
            handler.removeCallbacksAndMessages(null)
            webView.stopLoading()
            webView.destroy()
        }
        try {
            suspendCancellableCoroutine { cont ->
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, finishedUrl: String) {
                        // SSO chains through several redirects; keep probing
                        // after each page settle until a key shows up.
                        probe(view)
                    }

                    private fun probe(view: WebView) {
                        // Only the service's own origin can ever hold its
                        // apikey bootstrap global; SSO pages are never probed.
                        val current = view.url ?: return
                        if (!sameOrigin(current, url)) return
                        probeCount++
                        view.evaluateJavascript(probeJs(appType)) { raw ->
                            val key = parseProbe(raw)
                            if (key != null && !settled) {
                                settled = true
                                cont.resume(key)
                            } else if (!settled && !destroyed && probeCount < MAX_PROBES) {
                                // The SPA may still be booting; retry shortly.
                                handler.postDelayed(
                                    { if (!settled && !destroyed) probe(view) },
                                    PROBE_RETRY_MS,
                                )
                            }
                        }
                    }
                }
                cont.invokeOnCancellation {
                    // Cancellation can be signalled from any thread; hop to
                    // the main looper. The guard inside destroyWebView makes
                    // this exactly-once with the finally below.
                    handler.post { destroyWebView() }
                }
                webView.loadUrl(url)
            }
        } finally {
            // withContext resumes this block on the main thread even after
            // cancellation, so the WebView dies on its owning thread,
            // exactly once, on every path.
            destroyWebView()
        }
    }

    /** Reads only [appType]'s own bootstrap global; no generic fallbacks. */
    private fun probeJs(appType: AppType): String {
        val global = when (appType) {
            AppType.RADARR -> "window.Radarr && window.Radarr.apikey"
            AppType.SONARR -> "window.Sonarr && window.Sonarr.apikey"
            AppType.LIDARR -> "window.Lidarr && window.Lidarr.apikey"
            AppType.PROWLARR -> "window.Prowlarr && window.Prowlarr.apikey"
            AppType.BAZARR -> "window.Bazarr && (window.Bazarr.apiKey || window.Bazarr.apikey)"
        }
        return "(function(){ try { return $global || null; } catch (e) { return null; } })()"
    }

    /** Unwraps evaluateJavascript's JSON reply; null when no usable key. */
    private fun parseProbe(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "null" || raw == "undefined") return null
        val key = raw.trim()
            .removeSurrounding("\"")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .trim()
        return key.takeIf { it.length >= 16 && it.none { c -> c == ' ' } }
    }

    /** True when both URLs share scheme, host, and effective port. */
    private fun sameOrigin(a: String, b: String): Boolean {
        val ua = Uri.parse(a)
        val ub = Uri.parse(b)
        val hostA = ua.host ?: return false
        val hostB = ub.host ?: return false
        return ua.scheme.equals(ub.scheme, ignoreCase = true) &&
            hostA.equals(hostB, ignoreCase = true) &&
            effectivePort(ua) == effectivePort(ub)
    }

    private fun effectivePort(u: Uri): Int = when {
        u.port != -1 -> u.port
        u.scheme.equals("https", ignoreCase = true) -> 443
        else -> 80
    }

    /**
     * One authenticated GET against the app's status endpoint. Redirects
     * are refused (a 3xx here is an SSO login bounce) and only the app's
     * own JSON identity counts, so an HTML login page never passes.
     */
    private fun verify(base: String, apiKey: String, appType: AppType): Boolean = runCatching {
        // Built for this call alone: the dashboard client shares pools and
        // redirect behavior with the UI, which this check must not inherit.
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            // SSO-fronted servers only serve JSON to the browser session;
            // attach the WebView cookie for this origin like the factory does.
            .addInterceptor(
                dev.appolon.homelab.arr.net.SessionCookieInterceptorAccessible
                    .forBase(baseUrl = base, cookieHeaderProvider = ::webSessionCookie),
            )
            .build()
        try {
            STATUS_PATHS.any { path ->
                val request = okhttp3.Request.Builder()
                    .url(base.trimEnd('/') + path)
                    .header("X-Api-Key", apiKey)
                    .build()
                client.newCall(request).execute().use { response ->
                    response.isSuccessful &&
                        response.body?.string()?.let { isAppStatus(it, appType) } == true
                }
            }
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }.getOrDefault(false)

    /**
     * A genuine status payload is JSON naming the app itself: `appName`
     * for the four Servarr apps, a `data` envelope for Bazarr. HTML and
     * login pages fail the JSON parse or the identity check.
     */
    private fun isAppStatus(body: String, appType: AppType): Boolean {
        val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject
            ?: return false
        return if (appType == AppType.BAZARR) {
            root["data"] is JsonObject
        } else {
            (root["appName"] as? JsonPrimitive)?.content
                ?.equals(appType.wireName, ignoreCase = true) == true
        }
    }
}
