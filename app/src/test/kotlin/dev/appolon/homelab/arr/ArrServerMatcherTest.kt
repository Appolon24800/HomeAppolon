package dev.appolon.homelab.arr

import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Contract tests for the dashboard-URL-to-server mapping. The cases double
 * as documentation of which Homer entries open the native shell.
 */
class ArrServerMatcherTest {

    private fun server(id: String, baseUrl: String, type: AppType = AppType.RADARR): ArrServer =
        ArrServer(
            id = ServerId(id),
            type = type,
            name = id,
            baseUrl = baseUrl,
            apiKeyEncrypted = "v1:test",
        )

    private fun match(url: String, vararg servers: ArrServer): ServerId? =
        ArrServerMatcher.match(url, servers.toList())

    @Test
    fun matchesRootServiceUrl() {
        val id = match("http://media.lan:7878", server("radarr", "http://media.lan:7878/"))
        assertEquals(ServerId("radarr"), id)
    }

    @Test
    fun matchesServiceUrlWithDeeperPath() {
        val id = match(
            "http://nas.lan:30025/radarr/movie/12",
            server("radarr", "http://nas.lan:30025/radarr"),
        )
        assertEquals(ServerId("radarr"), id)
    }

    @Test
    fun matchesEqualPathsWithAndWithoutTrailingSlash() {
        val id = match(
            "http://nas.lan:30025/radarr/",
            server("radarr", "http://nas.lan:30025/radarr"),
        )
        assertEquals(ServerId("radarr"), id)
    }

    @Test
    fun matchesWhenServerBaseUrlKeepsItsTrailingSlash() {
        val id = match(
            "http://nas.lan:30025/radarr",
            server("radarr", "http://nas.lan:30025/radarr/"),
        )
        assertEquals(ServerId("radarr"), id)
    }

    @Test
    fun rejectsSiblingPathThatSharesThePrefix() {
        assertNull(
            match("http://nas.lan:30025/radarr2", server("radarr", "http://nas.lan:30025/radarr")),
        )
    }

    @Test
    fun rejectsDifferentPathOnSameOrigin() {
        assertNull(
            match("http://nas.lan:8989/sonarr", server("radarr", "http://nas.lan:8989/radarr")),
        )
    }

    @Test
    fun rejectsPortMismatch() {
        assertNull(match("http://nas.lan:7878", server("radarr", "http://nas.lan:8989")))
    }

    @Test
    fun rejectsPortMismatchWithPathPrefix() {
        // Both ports are explicit and differ: the loose rule must not fire.
        assertNull(
            match(
                "http://nas.lan:30025/radarr/status",
                server("radarr", "http://nas.lan:30026/radarr"),
            ),
        )
    }

    @Test
    fun rejectsHostMismatch() {
        assertNull(match("http://other.lan:7878", server("radarr", "http://nas.lan:7878")))
    }

    @Test
    fun rejectsSchemeMismatch() {
        assertNull(match("https://nas.lan:7878", server("radarr", "http://nas.lan:7878")))
    }

    @Test
    fun ignoresHostCase() {
        val id = match("HTTP://MEDIA.LAN:7878", server("radarr", "http://media.lan:7878"))
        assertEquals(ServerId("radarr"), id)
    }

    @Test
    fun looseRuleMatchesPathPrefixInsideProxiedUrl() {
        // Service URL has no explicit port, server does: same host plus a
        // real path prefix inside the URL is enough.
        val id = match(
            "http://nas.lan/apps/sonarr",
            server("sonarr", "http://nas.lan:8989/sonarr", AppType.SONARR),
        )
        assertEquals(ServerId("sonarr"), id)
    }

    @Test
    fun returnsNullForEmptyServerList() {
        assertNull(match("http://nas.lan:7878"))
    }

    @Test
    fun returnsNullForBlankOrMalformedServiceUrl() {
        assertNull(match("", server("radarr", "http://nas.lan:7878")))
        assertNull(match("not a url", server("radarr", "http://nas.lan:7878")))
        assertNull(match("http://[bad", server("radarr", "http://nas.lan:7878")))
    }

    @Test
    fun skipsServerWithMalformedBaseUrl() {
        val id = match(
            "http://nas.lan:7878",
            server("broken", "http://[bad"),
            server("radarr", "http://nas.lan:7878"),
        )
        assertEquals(ServerId("radarr"), id)
    }

    @Test
    fun firstMatchingServerWins() {
        val id = match(
            "http://nas.lan:7878",
            server("first", "http://nas.lan:7878"),
            server("second", "http://nas.lan:7878"),
        )
        assertEquals(ServerId("first"), id)
    }
}
