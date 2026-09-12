package dev.appolon.homelab.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.appolon.homelab.data.WebTarget

/**
 * A WebView that refuses to be a text editor until the user actually touches
 * it. Pages like Jellyfin auto-focus a login field on load; without this the
 * IME flashes open, the page lays itself out for the IME-sized viewport, and
 * then never recomputes when the keyboard closes — leaving the site rendered
 * in the top half of the screen. With the touch gate, the keyboard only ever
 * appears for real user taps.
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
 * Full-screen in-app browser for a service page. System back walks the page
 * history first, then leaves; the top-bar arrow leaves immediately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebScreen(target: WebTarget, onClose: () -> Unit) {
    val context = LocalContext.current
    var progress by remember { mutableStateOf(100) }
    var pageTitle by remember { mutableStateOf<String?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }

    val webView = remember {
        TouchGatedWebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
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
                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    failed = false
                }

                override fun onPageFinished(view: WebView, url: String) {
                    canGoBack = view.canGoBack()
                    pageTitle = view.title
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

    DisposableEffect(target.url) {
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
        loadIfReady() // view may already be laid out (target change)
        webView.addOnLayoutChangeListener(listener)

        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            webView.removeOnLayoutChangeListener(listener)
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            webView.destroy()
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

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = pageTitle ?: target.initialTitle ?: target.url,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close page")
                        }
                    },
                    actions = {
                        var menuOpen by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Page menu")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Reload") },
                                leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                                onClick = { menuOpen = false; reloadTick++ },
                            )
                            DropdownMenuItem(
                                text = { Text("Open in browser") },
                                onClick = {
                                    menuOpen = false
                                    Urls.open(context, webView.url ?: target.url)
                                },
                            )
                        }
                    },
                )
                if (progress < 100) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (failed) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
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
        } else {
            AndroidView(
                factory = { webView },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    // Drive IME handling through Compose: the WebView resizes
                    // (and, crucially, re-expands) with layout passes instead of
                    // stale window resizes from adjustResize.
                    .imePadding(),
            )
        }
    }
}
