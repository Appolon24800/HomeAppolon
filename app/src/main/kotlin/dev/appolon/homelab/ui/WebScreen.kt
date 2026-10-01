package dev.appolon.homelab.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.appolon.homelab.data.WebTarget

/**
 * A WebView that refuses to be a text editor until the user actually touches
 * it, so a page's programmatic input.focus() on load cannot pop the IME
 * (which resizes the view and can desync the renderer's layout viewport).
 */
private class TouchGatedWebView(context: Context) : WebView(context) {
    private var userTouched = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        userTouched = true
        return super.onTouchEvent(event)
    }

    override fun onCheckIsTextEditor(): Boolean = userTouched && super.onCheckIsTextEditor()
}

/**
 * vh vs innerHeight: when they disagree the renderer's layout viewport has
 * collapsed (percentage/vh/absolute layouts then render as a thin slice at
 * the top of the view).
 */
private const val VIEWPORT_PROBE =
    "(function(){var d=document.createElement('div');" +
        "d.style.cssText='position:absolute;height:100vh;visibility:hidden';" +
        "document.body.appendChild(d);var h=d.getBoundingClientRect().height;d.remove();" +
        "return h+'|'+window.innerHeight;})()"

private val mainHandler = Handler(Looper.getMainLooper())

/**
 * Full-screen in-app browser for a service page. System back walks the page
 * history first, then leaves; the top-bar arrow leaves immediately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebScreen(
    target: WebTarget,
    onClose: () -> Unit,
    authBaseUrl: String = dev.appolon.homelab.data.PocketId.WELL_KNOWN_BASE_URL,
) {
    val context = LocalContext.current
    var progress by remember { mutableStateOf(100) }
    var pageTitle by remember { mutableStateOf<String?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }

    // The emulator's WebView has a renderer bug where the layout viewport
    // collapses to zero when resizes race a page load (vh/percent layouts
    // then break — visible as the site rendering in a top slice). Detected
    // via VIEWPORT_PROBE after the load settles; healed by swapping in a
    // fresh WebView instance once. Capped at one attempt: stability beats a
    // perfect page, and the overflow menu has "Open in browser" as the
    // escape hatch.
    var webViewKey by remember { mutableIntStateOf(0) }
    var healAttempts by remember { mutableIntStateOf(0) }

    val webView = remember(webViewKey) {
        TouchGatedWebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            // Browser-style passkeys: any site (PocketID, Vaultwarden, …) can
            // run WebAuthn through Credential Manager, without per-domain
            // Digital Asset Links. FOR_APP would limit ceremonies to domains
            // linked via assetlinks.json, which broke Vaultwarden.
            if (androidx.webkit.WebViewFeature.isFeatureSupported(
                    androidx.webkit.WebViewFeature.WEB_AUTHENTICATION,
                )
            ) {
                androidx.webkit.WebSettingsCompat.setWebAuthenticationSupport(
                    settings,
                    androidx.webkit.WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER,
                )
            }
            // Follow the system dark theme: without this, pages like Sonarr
            // render light-only even when the device is in dark mode.
            if (androidx.webkit.WebViewFeature.isFeatureSupported(
                    androidx.webkit.WebViewFeature.ALGORITHMIC_DARKENING,
                )
            ) {
                androidx.webkit.WebSettingsCompat.setAlgorithmicDarkeningAllowed(
                    settings,
                    true,
                )
            }
            // Some WebView providers (e.g. this emulator's) default to a desktop
            // UA, which makes UA-sniffing sites serve their desktop layout.
            // Rewrite to a Chrome-on-Android UA only when the default isn't mobile.
            if (!settings.userAgentString.orEmpty().contains("Mobile")) {
                val chrome = Regex("Chrome/[\\d.]+")
                    .find(settings.userAgentString.orEmpty())?.value ?: "Chrome/141.0.0.0"
                settings.userAgentString =
                    "Mozilla/5.0 (Linux; Android ${android.os.Build.VERSION.RELEASE}; " +
                        "${android.os.Build.MODEL}) AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "$chrome Mobile Safari/537.36"
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    // PocketID's error page buttons link to
                    // `document.referrer || '/'`; inside a WebView the referrer
                    // is empty, so "Go back" would navigate to the auth host's
                    // root and strand the user on the login site. With history
                    // available, treat that root hit as a back navigation.
                    val dest = request.url
                    val authRoot = Uri.parse(authBaseUrl)
                    val isAuthRoot = dest.host == authRoot.host &&
                        (dest.path == null || dest.path == "/")
                    if (isAuthRoot && view.canGoBack()) {
                        view.goBack()
                        return true
                    }
                    return false
                }

                override fun onPageStarted(
                    view: WebView,
                    url: String,
                    favicon: android.graphics.Bitmap?,
                ) {
                    failed = false
                }

                override fun onPageFinished(view: WebView, url: String) {
                    canGoBack = view.canGoBack()
                    pageTitle = view.title
                    if (healAttempts >= 1) return
                    var settles = 0
                    fun probe() {
                        runCatching {
                            if (progress < 100 && settles < 5) {
                                settles++
                                mainHandler.postDelayed({ runCatching { probe() } }, 1_200)
                                return@runCatching
                            }
                            view.evaluateJavascript(VIEWPORT_PROBE) { result ->
                                runCatching {
                                    val parts = result?.trim('"')?.split("|") ?: emptyList()
                                    val vh = parts.getOrNull(0)?.toFloatOrNull() ?: 0f
                                    val ih = parts.getOrNull(1)?.toFloatOrNull() ?: 0f
                                    if (ih <= 0f || vh >= ih * 0.5f) return@runCatching
                                    if (healAttempts >= 1) return@runCatching
                                    healAttempts++
                                    webViewKey++
                                }
                            }
                        }
                    }
                    mainHandler.postDelayed({ runCatching { probe() } }, 1_200)
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    if (request.isForMainFrame) failed = true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progress = newProgress
                }

                override fun onReceivedTitle(view: WebView, title: String?) {
                    pageTitle = title
                }
            }
            // Hand downloads (e.g. ebook files) to the system.
            setDownloadListener { url, _, _, _, _ ->
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
            // OIDC-style logins hop domains; they need cross-site cookies.
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        }
    }

    // Algorithmic darkening applies at render time. The activity handles uiMode
    // itself, so a mid-session theme flip would otherwise leave the current
    // page in the old colour scheme — reload to re-render it.
    val darkTheme = isSystemInDarkTheme()
    var renderedDark by remember(webViewKey) { mutableStateOf(darkTheme) }
    LaunchedEffect(darkTheme) {
        if (darkTheme != renderedDark) {
            renderedDark = darkTheme
            if (webView.url != null) webView.reload()
        }
    }

    DisposableEffect(webView) {
        progress = 100
        pageTitle = null
        canGoBack = false
        failed = false
        // Load only once the view has real dimensions: loading into an
        // unmeasured WebView makes overview mode compute its scale against a
        // fallback size, leaving the page permanently zoomed out.
        var loaded = false
        fun loadIfReady() {
            if (!loaded && webView.width > 0 && webView.height > 0) {
                loaded = true
                webView.loadUrl(target.url)
            }
        }

        val listener = View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (v === webView) loadIfReady()
        }
        loadIfReady() // view may already be laid out
        webView.addOnLayoutChangeListener(listener)

        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            webView.removeOnLayoutChangeListener(listener)
            mainHandler.post { runCatching { webView.destroy() } }
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(reloadTick) {
        if (reloadTick > 0) {
            failed = false
            webView.reload()
        }
    }

    // System back walks page history when possible, otherwise closes the page.
    // Always handled — letting it fall through would exit the app instead of
    // returning to the dashboard.
    BackHandler { if (canGoBack) webView.goBack() else onClose() }

    // Fullscreen page: no top bar, just a hairline progress indicator while
    // loading and the error state when the main frame fails.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // A plain container we swap WebView instances into manually:
        // recomposition-driven re-creation races Compose's view teardown
        // ("child already has a parent"), this is synchronous and safe.
        AndroidView(
            factory = { FrameLayout(context) },
            update = { container ->
                val current = webView
                if (container.getChildAt(0) !== current) {
                    container.removeAllViews()
                    container.addView(
                        current,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (progress < 100 && !failed) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            )
        }
        if (failed) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    Text("Couldn't load the page", style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = { reloadTick++ },
                        modifier = Modifier.padding(top = 16.dp),
                    ) { Text("Retry") }
                }
            }
        }
    }
}
