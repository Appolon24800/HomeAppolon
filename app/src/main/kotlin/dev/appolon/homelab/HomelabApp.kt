package dev.appolon.homelab

import android.app.Application
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import dev.appolon.homelab.data.Network

class HomelabApp : Application() {

    override fun onCreate() {
        super.onCreate()
        imageLoader = ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory())
                add(OkHttpNetworkFetcherFactory(Network.client))
            }
            .crossfade(true)
            .build()
    }

    companion object {
        lateinit var imageLoader: ImageLoader
            private set
    }
}
