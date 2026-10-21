package dev.appolon.homelab.arr

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.net.ServerEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Auto-provisions an *arr server from a plain dashboard link.
 *
 * The user's services sit behind SSO, and the app's WebView already holds
 * that session. So instead of asking for the API key, this loads the link
 * in a headless WebView that shares those cookies; once the *arr web app
 * renders, its own bootstrap global (`window.Radarr.apikey` and siblings)
 * hands us the key. We verify it against the server's status endpoint,
 * store the server, and hand back its id: the native engine opens with
 * zero manual setup.
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

    /** Probes every *arr bootstrap global plus the common localStorage keys. */
    private const val PROBE_JS =
        """(function(){
            var out = {};
            try { out.radarr = window.Radarr && window.Radarr.apikey; } catch (e) {}
            try { out.sonarr = window.Sonarr && window.Sonarr.apikey; } catch (e) {}
            try { out.lidarr = window.Lidarr && window.Lidarr.apikey; } catch (e) {}
            try { out.prowlarr = window.Prowlarr && window.Prowlarr.apikey; } catch (e) {}
            try { out.bazarr = window.Bazarr && (window.Bazarr.apiKey || window.Bazarr.apikey); } catch (e) {}
            try {
                for (var i = 0; i < localStorage.length; i++) {
                    var k = localStorage.key(i);
                    if (k && k.toLowerCase().indexOf('apikey') >= 0) {
                        var v = localStorage.getItem(k);
                        if (v && !out.any) { out.any = v.replace(/^["']|["']$/g, ''); }
                    }
                }
            } catch (e) {}
            return JSON.stringify(out);
        })()"""

    /** Wall-clock budget for the whole invisible round trip. */
    private val TIMEOUT_MS = 15_000L

    /**
     * Resolves [serviceUrl] to a configured server, creating one when the
     * key extraction succeeds. Runs the WebView on the main thread; safe
     * to call from any coroutine scope.
     */
    suspend fun connect(context: Context, serviceUrl: String, appType: AppType): Outcome {
        val key = withTimeoutOrNull(TIMEOUT_MS) {
            extractApiKey(context.applicationContext, serviceUrl)
        } ?: return Outcome.NeedSetup
        if (key.isNullOrBlank() || key.length < 16) return Outcome.NeedSetup

        val base = ServerEndpoint.normalizeBaseUrl(serviceUrl)
        return when (verify(base, key, context)) {
            true -> {
                val runtime = ArrBootstrap.runtime(context.applicationContext)
                val graph = runtime.graph
                val existing = graph.registry.servers().firstOrNull {
                    it.type == appType && it.baseUrl.trimEnd('/') == base.trimEnd('/')
                }
                val server = graph.registry.upsert(
                    existing?.copy(apiKeyEncrypted = graph.secrets.encrypt(key))
                        ?: dev.appolon.homelab.arr.data.ArrServer(
                            id = ServerId(java.util.UUID.randomUUID().toString()),
                            type = appType,
                            name = appType.displayName,
                            baseUrl = base,
                            apiKeyEncrypted = graph.secrets.encrypt(key),
                        ),
                )
                Outcome.Connected(server.id)
            }
            false -> Outcome.NeedSetup
        }
    }

    /** Loads the page invisibly and returns the apikey its UI exposes. */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun extractApiKey(context: Context, url: String): String? =
        withContext(Dispatchers.Main) {
            val webView = WebView(context)
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            CookieManager.getInstance().let { it.setAcceptCookie(true); it.acceptThirdPartyCookies(webView) }

            var settled = false
            var probeCount = 0
            suspendCancellableCoroutine { cont ->
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, finishedUrl: String) {
                        // SSO chains through several redirects; keep probing
                        // after each page settle until a key shows up.
                        probe(view)
                    }

                    private fun probe(view: WebView) {
                        probeCount++
                        view.evaluateJavascript(PROBE_JS) { raw ->
                            val key = parseProbe(raw)
                            if (key != null && !settled) {
                                settled = true
                                cont.resume(key)
                            } else if (!settled && probeCount < 12) {
                                // The SPA may still be booting; retry shortly.
                                view.postDelayed({ if (!settled) probe(view) }, 700L)
                            }
                        }
                    }
                }
                cont.invokeOnCancellation {
                    webView.post {
                        webView.stopLoading()
                        webView.destroy()
                    }
                }
                webView.loadUrl(url)
            }.also { key ->
                // Best effort cleanup either way.
                webView.post {
                    webView.stopLoading()
                    webView.destroy()
                }
                key
            }
        }

    /** Pulls the first plausible key out of the probe's JSON reply. */
    private fun parseProbe(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "null") return null
        val body = raw.trim('"').replace("\\\"", "\"")
        val match = Regex("\"(?:radarr|sonarr|lidarr|prowlarr|bazarr|any)\"\\s*:\\s*\"([^\"]+)\"")
            .find(body) ?: return null
        val key = match.groupValues[1].trim()
        return key.takeIf { it.length >= 16 && it.none { c -> c == ' ' } }
    }

    /** One authenticated GET against the app's status endpoint. */
    private fun verify(base: String, apiKey: String, @Suppress("UNUSED_PARAMETER") context: Context): Boolean = runCatching {
        val client = dev.appolon.homelab.data.Network.client.newBuilder()
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
        val paths = listOf("/api/v3/system/status", "/api/v1/system/status", "/api/system/status")
        paths.forEach { path ->
            val request = okhttp3.Request.Builder()
                .url(base.trimEnd('/') + path)
                .header("X-Api-Key", apiKey)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) return true
            }
        }
        false
    }.getOrDefault(false)
}
