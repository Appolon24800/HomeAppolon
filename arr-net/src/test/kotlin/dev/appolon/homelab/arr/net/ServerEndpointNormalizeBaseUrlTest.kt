package dev.appolon.homelab.arr.net

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Exhaustive behavior lock for the read-only normalizeBaseUrl contract: scheme
 * addition, path prefix retention, trailing slash handling, ports, IPv4 and
 * IPv6 literals, and stray whitespace.
 */
class ServerEndpointNormalizeBaseUrlTest {
    @Test
    fun `adds http scheme when missing`() {
        assertEquals(
            "http://media.example/",
            ServerEndpoint.normalizeBaseUrl("media.example"),
        )
    }

    @Test
    fun `keeps http scheme`() {
        assertEquals(
            "http://media.example/",
            ServerEndpoint.normalizeBaseUrl("http://media.example"),
        )
    }

    @Test
    fun `keeps https scheme`() {
        assertEquals(
            "https://media.example/",
            ServerEndpoint.normalizeBaseUrl("https://media.example"),
        )
    }

    @Test
    fun `adds scheme before bare host with port`() {
        assertEquals(
            "http://media.example:30025/",
            ServerEndpoint.normalizeBaseUrl("media.example:30025"),
        )
    }

    @Test
    fun `keeps port with scheme`() {
        assertEquals(
            "http://media.example:30025/",
            ServerEndpoint.normalizeBaseUrl("http://media.example:30025"),
        )
    }

    @Test
    fun `retains path prefix`() {
        assertEquals(
            "http://host:30025/radarr/",
            ServerEndpoint.normalizeBaseUrl("http://host:30025/radarr"),
        )
    }

    @Test
    fun `collapses duplicate trailing slashes into one`() {
        assertEquals(
            "http://host:30025/radarr/",
            ServerEndpoint.normalizeBaseUrl("http://host:30025/radarr//"),
        )
    }

    @Test
    fun `trailing slash is added exactly once`() {
        assertEquals(
            "http://host:30025/radarr/",
            ServerEndpoint.normalizeBaseUrl("http://host:30025/radarr/"),
        )
    }

    @Test
    fun `retains multi segment path`() {
        assertEquals("http://host/a/b/", ServerEndpoint.normalizeBaseUrl("http://host/a/b"))
    }

    @Test
    fun `normalizes ipv4 literal`() {
        assertEquals("http://192.168.1.10/", ServerEndpoint.normalizeBaseUrl("192.168.1.10"))
    }

    @Test
    fun `normalizes ipv4 literal with port`() {
        assertEquals(
            "http://192.168.1.10:8310/",
            ServerEndpoint.normalizeBaseUrl("192.168.1.10:8310"),
        )
    }

    @Test
    fun `normalizes bracketed ipv6 literal`() {
        assertEquals("http://[::1]/", ServerEndpoint.normalizeBaseUrl("[::1]"))
    }

    @Test
    fun `normalizes bracketed ipv6 literal with port`() {
        assertEquals(
            "http://[2001:db8::1]:8310/",
            ServerEndpoint.normalizeBaseUrl("http://[2001:db8::1]:8310"),
        )
    }

    @Test
    fun `adds scheme to bracketed ipv6 literal`() {
        assertEquals(
            "http://[2001:db8::1]/",
            ServerEndpoint.normalizeBaseUrl("[2001:db8::1]"),
        )
    }

    @Test
    fun `strips leading and trailing spaces`() {
        assertEquals(
            "http://media.example/",
            ServerEndpoint.normalizeBaseUrl("  http://media.example  "),
        )
    }

    @Test
    fun `strips spaces around scheme-less host`() {
        assertEquals(
            "http://media.example:8310/",
            ServerEndpoint.normalizeBaseUrl("  media.example:8310  "),
        )
    }
}
