package dev.appolon.homelab.arr

import android.content.Context
import dev.appolon.homelab.arr.core.ServerId

/**
 * The single seam between the Homelab dashboard and the native *arr client,
 * as fixed by docs/ARR_ARCHITECTURE.md's integration contract. The dashboard
 * asks [openFor] before falling back to its WebView, and calls [onAppStart]
 * once per process so channels and the arr data graph exist from the first
 * frame.
 */
object ArrIntegration {

    /**
     * Resolves a dashboard service URL against the configured arr servers.
     * Returns the matching server's id, or null when the URL belongs to no
     * arr server and the WebView path should run unchanged.
     */
    suspend fun openFor(context: Context, serviceUrl: String): ServerId? {
        if (serviceUrl.isBlank()) return null
        val servers = ArrBootstrap.runtime(context).graph.registry.servers()
        return ArrServerMatcher.match(serviceUrl, servers)
    }

    /**
     * Registers the arr notification channels (idempotent) and warms the arr
     * data graph so the first service tap resolves without a cold DataStore
     * read. Safe to call repeatedly.
     */
    fun onAppStart(context: Context) {
        ArrBootstrap.onAppStart(context)
    }
}
