package dev.appolon.homelab.arr.servers.logic

import dev.appolon.homelab.arr.core.AppType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusEndpointTest {

    @Test
    fun `path matches each app's api generation`() {
        assertEquals("/api/v3/system/status", StatusEndpoint.pathFor(AppType.RADARR))
        assertEquals("/api/v3/system/status", StatusEndpoint.pathFor(AppType.SONARR))
        assertEquals("/api/v1/system/status", StatusEndpoint.pathFor(AppType.LIDARR))
        assertEquals("/api/v1/system/status", StatusEndpoint.pathFor(AppType.PROWLARR))
        assertEquals("/api/system/status", StatusEndpoint.pathFor(AppType.BAZARR))
    }

    @Test
    fun `url keeps path prefix and trailing slash semantics`() {
        assertEquals(
            "http://host:30025/radarr/api/v3/system/status",
            StatusEndpoint.urlFor("http://host:30025/radarr", AppType.RADARR),
        )
        assertEquals(
            "https://host:7878/api/v1/system/status",
            StatusEndpoint.urlFor("https://host:7878/", AppType.PROWLARR),
        )
    }

    @Test
    fun `url adds a scheme when the user omitted it`() {
        assertEquals(
            "http://host:8989/api/v3/system/status",
            StatusEndpoint.urlFor("host:8989", AppType.SONARR),
        )
    }

    @Test
    fun `extract version from a status payload`() {
        assertEquals(
            "5.26.2.9999",
            StatusEndpoint.extractVersion("""{"version":"5.26.2.9999","appName":"Radarr"}"""),
        )
    }

    @Test
    fun `extract version tolerates missing unknown and malformed payloads`() {
        assertNull(StatusEndpoint.extractVersion("""{"appName":"Radarr"}"""))
        assertNull(StatusEndpoint.extractVersion("""{"version":null}"""))
        assertNull(StatusEndpoint.extractVersion("""{"version":""}"""))
        assertNull(StatusEndpoint.extractVersion("not json at all"))
        assertNull(StatusEndpoint.extractVersion(""))
    }

    @Test
    fun `extract version reads numeric versions as their string form`() {
        assertEquals("3", StatusEndpoint.extractVersion("""{"version":3}"""))
    }
}
