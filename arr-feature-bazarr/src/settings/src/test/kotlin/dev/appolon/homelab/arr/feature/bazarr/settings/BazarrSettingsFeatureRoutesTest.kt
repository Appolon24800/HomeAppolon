package dev.appolon.homelab.arr.feature.bazarr.settings

import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the route table the feature slot dispatches on: every pattern
 * captures exactly the serverId, and each screen pattern resolves to its own
 * shape so a dispatch can never fall through to the wrong destination.
 */
class BazarrSettingsFeatureRoutesTest {

    @Test
    fun `every pattern is unique`() {
        val patterns = BazarrSettingsFeature.PATTERNS
        assertEquals(patterns.size, patterns.toSet().size)
    }

    @Test
    fun `every pattern fills and matches back the server id`() {
        BazarrSettingsFeature.PATTERNS.forEach { pattern ->
            val filled = fillPattern(pattern, mapOf("serverId" to SERVER_ID))
            assertNotNull("pattern $pattern should fill", filled)
            val params = matchRoutePattern(pattern, requireNotNull(filled).split('/'))
            assertNotNull("pattern $pattern should match its own fill", params)
            assertEquals(mapOf("serverId" to SERVER_ID), params)
        }
    }

    @Test
    fun `each screen pattern has a distinct segment shape`() {
        val shapes = BazarrSettingsFeature.PATTERNS.associateWith { pattern ->
            pattern.split('/').map { if (it.startsWith("{")) "*" else it }
        }
        assertEquals(shapes.size, shapes.values.toSet().size)
    }

    @Test
    fun `foreign paths never match a settings pattern`() {
        listOf("settings", "settings/a/b/c/d", "other/s1/languages").forEach { path ->
            val segments = path.split('/')
            assertTrue(
                "path $path must not match",
                BazarrSettingsFeature.PATTERNS.all { matchRoutePattern(it, segments) == null },
            )
        }
    }

    private companion object {
        const val SERVER_ID = "srv-1"
    }
}
