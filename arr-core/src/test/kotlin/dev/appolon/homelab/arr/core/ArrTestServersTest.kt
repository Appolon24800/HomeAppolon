package dev.appolon.homelab.arr.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.AssumptionViolatedException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Exercises the rule and reader against synthetic properties files only. The
 * machine's real local.arr-test.properties is never read here: every test
 * redirects the reader via the arr.test.properties system property.
 */
class ArrTestServersTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val rule = ArrTestServersRule()

    @After
    fun restoreReader() {
        System.clearProperty("arr.test.properties")
        ArrTestServers.resetForTest()
    }

    private fun install(content: String) {
        val file = tmp.newFile("arr.properties")
        file.writeText(content)
        System.setProperty("arr.test.properties", file.absolutePath)
        ArrTestServers.resetForTest()
    }

    private fun pointAtMissingFile() {
        System.setProperty("arr.test.properties", "/nonexistent/arr.properties")
        ArrTestServers.resetForTest()
    }

    @Test
    fun parsesConfiguredAppsOnly() {
        install(
            """
            radarr.url=http://host-1:7878/radarr
            radarr.apikey=key-1
            sonarr.url=http://host-2:8989
            """.trimIndent()
        )
        assertTrue(ArrTestServers.available)
        val radarr = ArrTestServers.radarr
        assertEquals(AppType.RADARR, radarr?.app)
        assertEquals("http://host-1:7878/radarr", radarr?.baseUrl)
        assertEquals("key-1", radarr?.apiKey)
        // sonarr lacks its apikey, so it is not configured.
        assertNull(ArrTestServers.sonarr)
        assertNull(ArrTestServers.lidarr)
        assertNull(ArrTestServers.prowlarr)
        assertNull(ArrTestServers.bazarr)
    }

    @Test
    fun blankValuesCountAsUnconfigured() {
        install(
            """
            radarr.url=
            radarr.apikey=key-1
            """.trimIndent()
        )
        assertTrue(ArrTestServers.available)
        assertNull(ArrTestServers.server(AppType.RADARR))
    }

    @Test
    fun missingFileReportsUnavailable() {
        pointAtMissingFile()
        assertFalse(ArrTestServers.available)
        assertNull(ArrTestServers.radarr)
    }

    @Test
    fun ruleSelfSkipsWhenFileMissing() {
        pointAtMissingFile()
        val statement = rule.apply(
            object : org.junit.runners.model.Statement() {
                override fun evaluate() {
                    throw AssertionError("statement must not run without credentials")
                }
            },
            org.junit.runner.Description.EMPTY,
        )
        assertThrows(AssumptionViolatedException::class.java) {
            statement.evaluate()
        }
    }

    @Test
    fun requireSkipsUnconfiguredAppButReturnsConfigured() {
        install(
            """
            lidarr.url=http://host-3:8686
            lidarr.apikey=key-3
            """.trimIndent()
        )
        assertThrows(AssumptionViolatedException::class.java) {
            rule.require(AppType.RADARR)
        }
        assertEquals("http://host-3:8686", rule.require(AppType.LIDARR).baseUrl)
        assertNull(rule.get(AppType.RADARR))
    }

    @Test
    fun readerFallsBackToAbsentWhenOverrideFileNotAFile() {
        System.setProperty("arr.test.properties", tmp.root.absolutePath)
        ArrTestServers.resetForTest()
        assertFalse(ArrTestServers.available)
    }

    @Test
    fun valuesAreTrimmed() {
        install(
            """
            radarr.url=  http://host-1:7878
            radarr.apikey=key-1
            """.trimIndent()
        )
        assertEquals("http://host-1:7878", ArrTestServers.radarr?.baseUrl)
    }
}
