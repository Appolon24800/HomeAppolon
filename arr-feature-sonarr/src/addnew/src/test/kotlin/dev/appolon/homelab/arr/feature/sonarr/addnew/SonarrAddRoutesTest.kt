package dev.appolon.homelab.arr.feature.sonarr.addnew

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

/** Covers the add-new screen's nested route registration and decoding. */
class SonarrAddRoutesTest {

    @Test
    fun `register adds the add pattern once`() {
        val registry = ArrRouteRegistry()
        assertTrue(SonarrAddRoutes.register(registry))
        assertFalse(SonarrAddRoutes.register(registry))
        val nested = registry.nestedRoutes().single()
        assertEquals("sonarr", nested.owner)
        assertEquals(SonarrAddRoutes.ADD_PATTERN, nested.pattern)
    }

    @Test
    fun `addRoute encodes a decodable feature route`() {
        val registry = ArrRouteRegistry()
        SonarrAddRoutes.register(registry)
        val route = SonarrAddRoutes.addRoute(ServerId("server 1/x"))
        assertNotNull(route)
        val encoded = ArrRoutes.encode(route!!)
        val decoded = registry.decodeFeature(encoded)
        assertNotNull(decoded)
        assertEquals(ServerId("server 1/x"), SonarrAddRoutes.serverIdOf(decoded!!))
    }

    @Test
    fun `serverIdOf rejects other owners and shapes`() {
        assertNull(SonarrAddRoutes.serverIdOf(ArrRoute.Feature("radarr", "add/1")))
        assertNull(SonarrAddRoutes.serverIdOf(ArrRoute.Feature("sonarr", "library/1")))
    }
}
