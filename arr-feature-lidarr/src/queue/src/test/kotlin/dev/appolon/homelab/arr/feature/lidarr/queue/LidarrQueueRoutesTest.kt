package dev.appolon.homelab.arr.feature.lidarr.queue

import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.ArrRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the queue feature's nested route registration and decoding. */
class LidarrQueueRoutesTest {

    @Test
    fun `register adds both manual import patterns once`() {
        val registry = ArrRouteRegistry()
        LidarrQueueFeature.register(registry)
        LidarrQueueFeature.register(registry)
        assertEquals(
            listOf(
                LidarrQueueFeature.MANUAL_IMPORT_PATTERN,
                LidarrQueueFeature.MANUAL_IMPORT_DOWNLOAD_PATTERN,
            ),
            registry.nestedRoutes().map { it.pattern },
        )
        assertEquals(LidarrQueueFeature.ROUTE_OWNER, registry.nestedRoutes().first().owner)
    }

    @Test
    fun `folder route encodes a decodable feature route`() {
        val registry = ArrRouteRegistry()
        LidarrQueueFeature.register(registry)
        val route = LidarrQueueFeature.manualImportRoute(ServerId("server 1/x"), downloadId = null)
        assertNotNull(route)

        val decoded = registry.decodeFeature(ArrRoutes.encode(route!!))

        assertNotNull(decoded)
        // The registry returns the path once-decoded; parameters still carry
        // their percent escapes, which the slot decodes before use.
        assertEquals(
            listOf("queue", "manual-import", "server 1/x"),
            decoded!!.path.split('/').map(::decodeRouteSegment),
        )
    }

    @Test
    fun `download route carries the download id`() {
        val registry = ArrRouteRegistry()
        LidarrQueueFeature.register(registry)
        val route = LidarrQueueFeature.manualImportRoute(ServerId("s1"), downloadId = "abc-12")

        val decoded = registry.decodeFeature(ArrRoutes.encode(route!!))

        assertNotNull(decoded)
        assertTrue(decoded!!.path.split('/').map(::decodeRouteSegment).last() == "abc-12")
    }

    @Test
    fun `routes with an empty download id fall back to the folder route`() {
        val route = LidarrQueueFeature.manualImportRoute(ServerId("s1"), downloadId = "")
        assertNotNull(route)
        assertEquals("queue/manual-import/s1", route!!.path)
    }

    @Test
    fun `the queue patterns coexist with sibling lidarr patterns`() {
        val registry = ArrRouteRegistry()
        assertTrue(registry.register("lidarr-library", "artists/{serverId}"))
        LidarrQueueFeature.register(registry)

        val library = registry.decodeFeature("f/lidarr-library/artists/s1")
        val import = registry.decodeFeature("f/lidarr-queue/queue/manual-import/s1")
        assertEquals("artists/s1", library!!.path)
        assertEquals("queue/manual-import/s1", import!!.path)
    }

    @Test
    fun `decodeRouteSegment unescapes percent triples and keeps junk literal`() {
        assertEquals("server 1/x", decodeRouteSegment("server%201%2Fx"))
        assertEquals("plain", decodeRouteSegment("plain"))
        assertEquals("100%", decodeRouteSegment("100%"))
    }
}
