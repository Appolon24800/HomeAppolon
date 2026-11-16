package dev.appolon.homelab

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.appolon.homelab.arr.ArrAutoConnect
import dev.appolon.homelab.arr.ArrBootstrap
import dev.appolon.homelab.arr.core.AppType
import java.io.File
import java.util.Properties
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Optional device setup using a private input file, removed after provisioning. */
@RunWith(AndroidJUnit4::class)
class LiveServerProvisioningTest {
    @Test
    fun provisionLiveServersThroughTheProductionConnectionTester() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val input = File(context.filesDir, "live-arr-test.properties")
        assumeTrue("No private live-server setup file", input.isFile)
        try {
            val properties = Properties().apply { input.inputStream().use(::load) }
            val graph = ArrBootstrap.runtime(context).graph
            for (app in listOf(AppType.RADARR, AppType.SONARR, AppType.PROWLARR, AppType.BAZARR)) {
                val url = properties.getProperty("${app.wireName}.url") ?: continue
                val key = properties.getProperty("${app.wireName}.apikey") ?: continue
                val id = ArrAutoConnect.provision(context, app, url, key)
                assertNotNull("Live ${app.wireName} verification failed", id)
                val server = graph.registry.byId(checkNotNull(id)) ?: error("Verified server was not saved")
                graph.registry.upsert(server.copy(name = "Live ${app.wireName.replaceFirstChar(Char::titlecase)}"))
            }
        } finally {
            input.delete()
        }
    }
}
