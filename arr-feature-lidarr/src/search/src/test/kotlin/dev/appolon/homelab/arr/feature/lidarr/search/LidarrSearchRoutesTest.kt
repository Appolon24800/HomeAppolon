package dev.appolon.homelab.arr.feature.lidarr.search

import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.ArrRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the search feature's nested route registration and decoding. */
class LidarrSearchRoutesTest {

    @Test
    fun `register adds every pattern once under the search owner`() {
        val registry = ArrRouteRegistry()
        LidarrSearchFeature.register(registry)
        LidarrSearchFeature.register(registry)

        assertEquals(LidarrSearchFeature.PATTERNS, registry.nestedRoutes().map { it.pattern })
        assertTrue(registry.nestedRoutes().all { it.owner == LidarrSearchFeature.ROUTE_OWNER })
    }

    @Test
    fun `the three route builders produce decodable feature routes`() {
        val registry = ArrRouteRegistry()
        LidarrSearchFeature.register(registry)
        val routes = listOfNotNull(
            LidarrSearchFeature.artistReleasesRoute(ServerId("s1"), 5),
            LidarrSearchFeature.albumReleasesRoute(ServerId("s1"), 9),
            LidarrSearchFeature.interactiveImportRoute(ServerId("s1"), 5),
        )

        assertEquals(3, routes.size)
        routes.forEach { route ->
            val decoded = registry.decodeFeature(ArrRoutes.encode(route))
            assertNotNull(decoded)
            assertEquals(LidarrSearchFeature.ROUTE_OWNER, decoded!!.owner)
        }
    }

    @Test
    fun `artist and album release routes stay distinct after decoding`() {
        val registry = ArrRouteRegistry()
        LidarrSearchFeature.register(registry)

        val artist = registry.decodeFeature(
            ArrRoutes.encode(LidarrSearchFeature.artistReleasesRoute(ServerId("s1"), 5)!!),
        )
        val album = registry.decodeFeature(
            ArrRoutes.encode(LidarrSearchFeature.albumReleasesRoute(ServerId("s1"), 9)!!),
        )
        val import = registry.decodeFeature(
            ArrRoutes.encode(LidarrSearchFeature.interactiveImportRoute(ServerId("s1"), 5)!!),
        )

        assertEquals("releases/s1/artist/5", artist!!.path)
        assertEquals("releases/s1/album/9", album!!.path)
        assertEquals("import/s1/artist/5", import!!.path)
    }

    @Test
    fun `the search patterns coexist with sibling lidarr owners`() {
        val registry = ArrRouteRegistry()
        assertTrue(registry.register("lidarr-queue", "queue/manual-import/{serverId}"))
        LidarrSearchFeature.register(registry)

        val queue = registry.decodeFeature("f/lidarr-queue/queue/manual-import/s1")
        val releases = registry.decodeFeature("f/lidarr-search/releases/s1/album/9")

        assertEquals("queue/manual-import/s1", queue!!.path)
        assertEquals("releases/s1/album/9", releases!!.path)
    }

    @Test
    fun `route helpers accept only valid ids`() {
        assertNotNull(LidarrSearchFeature.artistReleasesRoute(ServerId("s1"), 5))
        // fillPattern rejects empty parameters, so a blank server id yields no route.
        assertEquals(null, LidarrSearchFeature.artistReleasesRoute(ServerId(""), 5))
        assertEquals(null, LidarrSearchFeature.albumReleasesRoute(ServerId(""), 9))
        assertEquals(null, LidarrSearchFeature.interactiveImportRoute(ServerId(""), 5))
    }
}
