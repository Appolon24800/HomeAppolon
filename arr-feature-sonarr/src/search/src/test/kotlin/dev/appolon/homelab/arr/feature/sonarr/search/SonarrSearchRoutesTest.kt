package dev.appolon.homelab.arr.feature.sonarr.search

import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.ArrRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the search feature's nested route registration and decoding. */
class SonarrSearchRoutesTest {

    @Test
    fun `register adds every pattern once under the search owner`() {
        val registry = ArrRouteRegistry()
        SonarrSearchFeature.register(registry)
        SonarrSearchFeature.register(registry)

        assertEquals(SonarrSearchFeature.PATTERNS, registry.nestedRoutes().map { it.pattern })
        assertTrue(registry.nestedRoutes().all { it.owner == SonarrSearchFeature.ROUTE_OWNER })
    }

    @Test
    fun `the five route builders produce decodable feature routes`() {
        val registry = ArrRouteRegistry()
        SonarrSearchFeature.register(registry)
        val routes = listOfNotNull(
            SonarrSearchFeature.seriesReleasesRoute(ServerId("s1"), 5),
            SonarrSearchFeature.seasonReleasesRoute(ServerId("s1"), 5, 2),
            SonarrSearchFeature.episodeReleasesRoute(ServerId("s1"), 5, 42),
            SonarrSearchFeature.interactiveImportRoute(ServerId("s1"), 5),
            SonarrSearchFeature.interactiveImportRoute(ServerId("s1"), 5, seasonNumber = 3),
        )

        assertEquals(5, routes.size)
        routes.forEach { route ->
            val decoded = registry.decodeFeature(ArrRoutes.encode(route))
            assertNotNull(decoded)
            assertEquals(SonarrSearchFeature.ROUTE_OWNER, decoded!!.owner)
        }
    }

    @Test
    fun `season and series release routes stay distinct after decoding`() {
        val registry = ArrRouteRegistry()
        SonarrSearchFeature.register(registry)

        val series = registry.decodeFeature(
            ArrRoutes.encode(SonarrSearchFeature.seriesReleasesRoute(ServerId("s1"), 5)!!),
        )
        val season = registry.decodeFeature(
            ArrRoutes.encode(SonarrSearchFeature.seasonReleasesRoute(ServerId("s1"), 5, 2)!!),
        )
        val episode = registry.decodeFeature(
            ArrRoutes.encode(SonarrSearchFeature.episodeReleasesRoute(ServerId("s1"), 5, 9)!!),
        )

        assertEquals("releases/s1/5", series!!.path)
        assertEquals("releases/s1/5/season/2", season!!.path)
        assertEquals("releases/s1/5/episode/9", episode!!.path)
    }

    @Test
    fun `the search patterns coexist with sibling sonarr owners`() {
        val registry = ArrRouteRegistry()
        assertTrue(registry.register("sonarr", "library/{serverId}"))
        SonarrSearchFeature.register(registry)

        val library = registry.decodeFeature("f/sonarr/library/s1")
        val releases = registry.decodeFeature("f/sonarr-search/releases/s1/5/season/2")

        assertEquals("library/s1", library!!.path)
        assertEquals("releases/s1/5/season/2", releases!!.path)
    }

    @Test
    fun `route helpers accept only valid ids`() {
        assertNotNull(SonarrSearchFeature.seriesReleasesRoute(ServerId("s1"), 5))
        // fillPattern rejects empty parameters, so a blank server id yields no route.
        assertEquals(null, SonarrSearchFeature.seriesReleasesRoute(ServerId(""), 5))
    }
}
