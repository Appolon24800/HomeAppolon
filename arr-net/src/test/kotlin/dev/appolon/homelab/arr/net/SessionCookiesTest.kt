package dev.appolon.homelab.arr.net

import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.Request
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCookiesTest {

    private class MapCookieSource(private val map: Map<String, String>) : CookieSource {
        override fun cookiesFor(url: String): List<Cookie> = map.entries.map { (name, value) ->
            Cookie.Builder().name(name).value(value).domain("example.com").build()
        }
    }

    @Test
    fun `session cookie attached on same-origin request`() {
        val server = MockWebServer().apply { enqueue(MockResponse().setBody("ok")) }
        server.start()
        val client = OkHttpClient.Builder()
            .addInterceptor(
                SessionCookieInterceptor(
                    baseUrl = server.url("/").toString(),
                    source = MapCookieSource(mapOf("sid" to "abc123")),
                ),
            )
            .build()
        client.newCall(Request.Builder().url(server.url("/api/v3/series")).build()).execute()
        val recorded = server.takeRequest()
        assertTrue(recorded.getHeader("Cookie")?.contains("sid=abc123") == true)
        server.shutdown()
    }

    @Test
    fun `no session cookie on foreign origin`() {
        val server = MockWebServer().apply { enqueue(MockResponse().setBody("ok")) }
        server.start()
        val client = OkHttpClient.Builder()
            .addInterceptor(
                SessionCookieInterceptor(
                    baseUrl = "http://arr.internal/",
                    source = MapCookieSource(mapOf("sid" to "abc123")),
                ),
            )
            .build()
        client.newCall(Request.Builder().url(server.url("/img")).build()).execute()
        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Cookie"))
        server.shutdown()
    }

    @Test
    fun `empty source leaves requests untouched`() {
        val server = MockWebServer().apply { enqueue(MockResponse().setBody("ok")) }
        server.start()
        val client = OkHttpClient.Builder()
            .addInterceptor(
                SessionCookieInterceptor(server.url("/").toString(), CookieSource.None),
            )
            .build()
        client.newCall(Request.Builder().url(server.url("/x")).build()).execute()
        assertNull(server.takeRequest().getHeader("Cookie"))
        server.shutdown()
    }
}
