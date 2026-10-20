package dev.appolon.homelab.arr.feature.lidarr.settings.system

import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.ArrRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers this group's nested route registration and decoding. */
class LidarrSettingsSystemRoutesTest {

    @Test
    fun `register adds every pattern once`() {
        val registry = ArrRouteRegistry()
        LidarrSettingsSystemFeature.register(registry)
        LidarrSettingsSystemFeature.register(registry)

        assertEquals(LidarrSettingsSystemFeature.PATTERNS, registry.nestedRoutes().map { it.pattern })
        assertEquals(LidarrSettingsSystemFeature.ROUTE_OWNER, registry.nestedRoutes().first().owner)
    }

    @Test
    fun `every helper route encodes and decodes through the registry`() {
        val registry = ArrRouteRegistry()
        LidarrSettingsSystemFeature.register(registry)
        val serverId = ServerId("server 1/x")

        val routes = listOf(
            LidarrSettingsSystemFeature.generalRoute(serverId),
            LidarrSettingsSystemFeature.backupRoute(serverId),
            LidarrSettingsSystemFeature.updatesRoute(serverId),
            LidarrSettingsSystemFeature.systemStatusRoute(serverId),
            LidarrSettingsSystemFeature.tagsRoute(serverId),
            LidarrSettingsSystemFeature.autoTaggingRoute(serverId),
        )
        routes.forEach { route ->
            assertNotNull(route)
            val decoded = registry.decodeFeature(ArrRoutes.encode(route!!))
            assertNotNull(decoded)
            assertTrue(decoded!!.path.endsWith("server%201%2Fx"))
        }
    }

    @Test
    fun `editor route carries the rule id and new marker`() {
        val registry = ArrRouteRegistry()
        LidarrSettingsSystemFeature.register(registry)
        val serverId = ServerId("s1")

        val edit = registry.decodeFeature(
            ArrRoutes.encode(LidarrSettingsSystemFeature.autoTaggingEditorRoute(serverId, 7)!!),
        )
        assertEquals("settings/autotagging/s1/7", edit!!.path)

        val create = registry.decodeFeature(
            ArrRoutes.encode(LidarrSettingsSystemFeature.autoTaggingEditorRoute(serverId, null)!!),
        )
        assertEquals("settings/autotagging/s1/new", create!!.path)
    }

    @Test
    fun `the settings patterns coexist with sibling lidarr patterns`() {
        val registry = ArrRouteRegistry()
        assertTrue(registry.register("lidarr", "library/{serverId}"))
        LidarrSettingsSystemFeature.register(registry)

        val library = registry.decodeFeature("f/lidarr/library/s1")
        val general = registry.decodeFeature("f/lidarr/settings/general/s1")
        assertEquals("library/s1", library!!.path)
        assertEquals("settings/general/s1", general!!.path)
    }
}
