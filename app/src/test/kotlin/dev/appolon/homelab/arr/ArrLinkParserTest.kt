package dev.appolon.homelab.arr

import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArrLinkParserTest {

    @Test
    fun `notification link identifies a stored server without credentials`() {
        assertEquals(ServerId("server-1"), ArrLinkParser.serverIdForUrl("homelab://arr/server/server-1"))
    }

    @Test
    fun `notification links preserve reserved characters in the stored server id`() {
        assertEquals(
            ServerId("server + / %25 ü"),
            ArrLinkParser.serverIdForUrl("homelab://arr/server/server%20%2B%20%2F%20%2525%20%C3%BC"),
        )
    }

    @Test
    fun `notification links reject other routes and malformed ids`() {
        listOf(
            "https://arr/server/server-1",
            "homelab://other/server/server-1",
            "homelab://arr/server/",
            "homelab://arr/server/server-1/extra",
            "homelab://arr/server/server-1?url=http://elsewhere",
            "homelab://arr/server/server-1#fragment",
            "homelab://user@arr/server/server-1",
        ).forEach { assertNull(it, ArrLinkParser.serverIdForUrl(it)) }
    }

    @Test
    fun `explicit connect link with all parameters`() {
        val r = ArrLinkParser.parse(
            "homelab://arr/add?app=radarr&url=http://192.168.1.39:30025/radarr&apikey=abc123",
        )
        assertEquals(AppType.RADARR, r?.appType)
        assertEquals("http://192.168.1.39:30025/radarr/", r?.baseUrl)
        assertEquals("abc123", r?.apiKey)
    }

    @Test
    fun `connect link on the dashboard https domain`() {
        val r = ArrLinkParser.parse(
            "https://home.appolon.dev/arr/add?app=sonarr&url=http://192.168.1.39:30113/sonarr&apikey=k",
        )
        assertEquals(AppType.SONARR, r?.appType)
        assertEquals("http://192.168.1.39:30113/sonarr/", r?.baseUrl)
    }

    @Test
    fun `connect link derives type from url when app param missing`() {
        val r = ArrLinkParser.parse(
            "homelab://arr/add?url=https://lidarr.example.com/music&api_key=zz",
        )
        assertEquals(AppType.LIDARR, r?.appType)
        assertEquals("https://lidarr.example.com/music/", r?.baseUrl)
    }

    @Test
    fun `bare url with apikey query strips key and api path`() {
        val r = ArrLinkParser.parse(
            "http://192.168.1.39:30025/radarr/api/v3/system/status?apikey=abc123",
        )
        assertEquals(AppType.RADARR, r?.appType)
        assertEquals("http://192.168.1.39:30025/radarr/", r?.baseUrl)
        assertEquals("abc123", r?.apiKey)
    }

    @Test
    fun `subdomain url with key`() {
        val r = ArrLinkParser.parse("https://prowlarr.example.com/api/v1/health?apikey=k2")
        assertEquals(AppType.PROWLARR, r?.appType)
        assertEquals("https://prowlarr.example.com/", r?.baseUrl)
    }

    @Test
    fun `key in fragment is honored`() {
        val r = ArrLinkParser.parse("http://host:30046/bazarr/#apikey=fragkey")
        assertEquals(AppType.BAZARR, r?.appType)
        assertEquals("http://host:30046/bazarr/", r?.baseUrl)
        assertEquals("fragkey", r?.apiKey)
    }

    @Test
    fun `other query params survive key stripping`() {
        val r = ArrLinkParser.parse(
            "homelab://arr/add?app=lidarr&url=http://h/lidarr&apikey=k&note=keep",
        )
        assertEquals("http://h/lidarr/", r?.baseUrl)
    }

    @Test
    fun `url parameter key is stripped from the target`() {
        val r = ArrLinkParser.parse(
            "homelab://arr/add?app=radarr&url=http://h/radarr%3Fapikey%3Dleaked&apikey=real",
        )
        assertEquals("http://h/radarr/", r?.baseUrl)
        assertEquals("real", r?.apiKey)
    }

    @Test
    fun `non arr urls return null`() {
        assertNull(ArrLinkParser.parse("https://example.com/page"))
        assertNull(ArrLinkParser.parse("http://unknown.example.com/path?apikey=k"))
        assertNull(ArrLinkParser.parse("homelab://other/add?app=radarr&url=http://h/radarr&apikey=k"))
        assertNull(ArrLinkParser.parse("homelab://arr/add?app=radarr&apikey=k"))
        assertNull(ArrLinkParser.parse(""))
    }

    @Test
    fun `plain dashboard link without key returns null`() {
        assertNull(ArrLinkParser.parse("https://home.appolon.dev/"))
    }
}
