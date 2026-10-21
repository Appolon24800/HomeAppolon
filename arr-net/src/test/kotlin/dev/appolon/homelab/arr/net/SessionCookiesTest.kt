package dev.appolon.homelab.arr.net

import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.Request
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import dev.appolon.homelab.arr.core.ServerId

class SessionCookiesTest {

    @Test
    fun `default feature factories use the application session policy without leaking on redirect`() {
        val origin = MockWebServer()
        val foreign = MockWebServer()
        origin.start()
        foreign.start()
        val previous = ArrNetworkDefaults.cookieSource
        try {
            ArrNetworkDefaults.cookieSource = MapCookieSource(mapOf("sid" to "session"))
            origin.enqueue(MockResponse().setResponseCode(302).setHeader("Location", foreign.url("/login")))
            foreign.enqueue(MockResponse().setBody("ok"))
            val factory = DefaultArrClientFactory()
            val endpoint = ServerEndpoint(ServerId("cookie-test"), origin.url("/").toString(), "key")
            factory.clientFor(endpoint).newCall(Request.Builder().url(origin.url("/api")).build())
                .execute().use { assertTrue(it.isSuccessful) }
            assertTrue(origin.takeRequest().getHeader("Cookie")?.contains("sid=session") == true)
            val redirected = foreign.takeRequest()
            assertNull(redirected.getHeader("Cookie"))
            assertNull(redirected.getHeader("X-Api-Key"))

            origin.enqueue(MockResponse().setBody("image"))
            factory.imageClientFor(endpoint).newCall(Request.Builder().url(origin.url("/poster")).build())
                .execute().close()
            assertNull(origin.takeRequest().getHeader("Cookie"))
        } finally {
            ArrNetworkDefaults.cookieSource = previous
            origin.shutdown()
            foreign.shutdown()
        }
    }

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
