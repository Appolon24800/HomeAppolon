package dev.appolon.homelab

import android.app.Application
import android.content.pm.ApplicationInfo
import android.webkit.WebSettings
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import coil3.util.DebugLogger
import dev.appolon.homelab.data.Network

class HomelabApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable) {
            android.webkit.WebView.setWebContentsDebuggingEnabled(true)
        }
        enableWebViewPasskeys()
        imageLoader = ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory())
                add(OkHttpNetworkFetcherFactory(Network.client))
            }
            .crossfade(true)
            .apply { if (isDebuggable) logger(DebugLogger()) }
            .build()
    }

    companion object {
        lateinit var imageLoader: ImageLoader
            private set

        /**
         * Opt the app's WebViews into WebAuthn ("APP mode"): pages whose RP ID
         * this app is associated with through Digital Asset Links can run
         * passkey ceremonies natively (Credential Manager). This is what makes
         * PocketID's Authenticate button work inside the in-app browser.
         */
        var webViewPasskeysSupported: Boolean = false
            private set

        private fun enableWebViewPasskeys() {
            webViewPasskeysSupported = runCatching {
                WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)
            }.getOrDefault(false)
        }
    }
}
