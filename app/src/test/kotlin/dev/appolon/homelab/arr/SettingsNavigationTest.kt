package dev.appolon.homelab.arr

import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.bazarr.BazarrFeatureRoot
import dev.appolon.homelab.arr.feature.lidarr.LidarrFeatureRoot
import dev.appolon.homelab.arr.feature.prowlarr.ProwlarrFeatureRoot
import dev.appolon.homelab.arr.feature.radarr.RadarrFeatureRoot
import dev.appolon.homelab.arr.feature.sonarr.SonarrFeatureRoot
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.ArrRoutes
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.patternParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SettingsNavigationTest {
    private fun registry() = ArrRouteRegistry().also {
        RadarrFeatureRoot.registerRoutes(it)
        SonarrFeatureRoot.registerRoutes(it)
        LidarrFeatureRoot.registerRoutes(it)
        ProwlarrFeatureRoot.registerRoutes(it)
        BazarrFeatureRoot.registerRoutes(it)
    }

    @Test
    fun additionalAppMenusLinkEveryOtherwiseUnreachableFeatureGroup() {
        val registry = registry()
        val id = ServerId("server with + symbols")
        for ((app, count) in listOf(AppType.PROWLARR to 6, AppType.BAZARR to 5)) {
            val entries = additionalServerHomeEntries(app, id)
            assertEquals(count, entries.size)
            assertEquals(count, entries.map { it.route }.distinct().size)
            for (entry in entries) {
                val feature = entry.route as? dev.appolon.homelab.arr.ui.nav.ArrRoute.Feature ?: continue
                assertNotNull(registry.decodeFeature(ArrRoutes.encode(feature)))
            }
        }
    }

    @Test
    fun everyServarrSettingsPageHasALandingEntry() {
        val registry = registry()
        val id = ServerId("test-server")
        for (app in listOf(AppType.RADARR, AppType.SONARR, AppType.LIDARR)) {
            val expected = registry.nestedRoutes().filter {
                it.owner == app.wireName && it.pattern.startsWith("settings/") &&
                    patternParams(it.pattern) == listOf("serverId")
            }.map { fillPattern(it.pattern, mapOf("serverId" to id.value)) }.toSet()
            val entries = settingsSections(app, id).flatMap { it.entries }
            assertEquals(app.wireName, expected, entries.map { it.route.path }.toSet())
            assertEquals(entries.size, entries.map { it.route }.distinct().size)
        }
    }

    @Test
    fun allLandingLinksResolveToRegisteredRoutesForTheirServer() {
        val registry = registry()
        for (app in AppType.entries) {
            for (id in listOf(ServerId("regular"), ServerId("space and + symbols"))) {
                for (entry in settingsSections(app, id).flatMap { it.entries }) {
                    assertNotNull(registry.decodeFeature(ArrRoutes.encode(entry.route)))
                }
            }
        }
    }
}
