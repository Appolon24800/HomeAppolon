package dev.appolon.homelab.arr.feature.sonarr.library

import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.ArrRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the library's nested route registration and decoding. */
class SonarrLibraryRoutesTest {

    @Test
    fun `register adds the library pattern once`() {
        val registry = ArrRouteRegistry()
        assertTrue(SonarrLibraryRoutes.register(registry))
        assertFalse(SonarrLibraryRoutes.register(registry))
        val nested = registry.nestedRoutes().single()
        assertEquals("sonarr", nested.owner)
        assertEquals(SonarrLibraryRoutes.LIBRARY_PATTERN, nested.pattern)
    }

    @Test
    fun `libraryRoute encodes a decodable feature route`() {
        val registry = ArrRouteRegistry()
        SonarrLibraryRoutes.register(registry)
        val route = SonarrLibraryRoutes.libraryRoute(ServerId("server 1/x"))
        assertNotNull(route)
        val encoded = ArrRoutes.encode(route!!)
        val decoded = registry.decodeFeature(encoded)
        assertNotNull(decoded)
        assertEquals(ServerId("server 1/x"), SonarrLibraryRoutes.serverIdOf(decoded!!))
    }

    @Test
    fun `serverIdOf rejects other owners and shapes`() {
        assertNull(SonarrLibraryRoutes.serverIdOf(ArrRoute.Feature("radarr", "library/1")))
        assertNull(SonarrLibraryRoutes.serverIdOf(ArrRoute.Feature("sonarr", "queue/1")))
    }
}
