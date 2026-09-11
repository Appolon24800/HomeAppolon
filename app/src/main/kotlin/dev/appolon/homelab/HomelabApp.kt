package dev.appolon.homelab

import android.app.Application
import android.content.pm.ApplicationInfo
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
    }
}
