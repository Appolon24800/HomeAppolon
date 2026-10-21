package dev.appolon.homelab.arr.net

import dev.appolon.homelab.arr.core.ServerId
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class DefaultArrClientFactoryTest {
    private val server = MockWebServer()
    private val factory = DefaultArrClientFactory()

    private val endpoint = ServerEndpoint(
        serverId = ServerId("radarr-1"),
        baseUrl = "http://localhost/",
        apiKey = "test-key",
    )

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun url(): String = server.url("/api/v3/health").toString()

    private fun get(endpoint: ServerEndpoint): okhttp3.Response =
        factory.clientFor(endpoint).newCall(Request.Builder().url(url()).build()).execute()

    @Test
    fun `sends the api key and user agent on every request`() {
        server.enqueue(MockResponse().setBody("ok"))

        get(endpoint).use { response ->
            assertEquals(200, response.code)
        }

        val recorded = server.takeRequest()
        assertEquals("test-key", recorded.getHeader("X-Api-Key"))
        assertEquals("HomelabArr/1.0 (android)", recorded.getHeader("User-Agent"))
        assertNull(recorded.getHeader("Authorization"))
    }

    @Test
    fun `sends basic auth only when configured`() {
        server.enqueue(MockResponse().setBody("ok"))
        val withBasic = endpoint.copy(basicAuthUser = "media", basicAuthPassword = "homelab")

        get(withBasic).use { response ->
            assertEquals(200, response.code)
        }

        val recorded = server.takeRequest()
        assertEquals(
            HttpConnectProxy.basicAuthorization("media", "homelab"),
            recorded.getHeader("Authorization"),
        )
    }

    @Test
    fun `applies twenty second timeouts`() {
        val client = factory.clientFor(endpoint)

        assertEquals(20_000, client.connectTimeoutMillis)
        assertEquals(20_000, client.readTimeoutMillis)
        assertEquals(20_000, client.writeTimeoutMillis)
    }

    @Test
    fun `caches one client per server`() {
        assertSame(factory.clientFor(endpoint), factory.clientFor(endpoint))
    }

    @Test
    fun `invalidate forces a rebuild`() {
        val first = factory.clientFor(endpoint)
        factory.invalidate(ServerId("radarr-1"))

        assertNotSame(first, factory.clientFor(endpoint))
    }

    @Test
    fun `invalidate leaves other servers alone`() {
        val other = endpoint.copy(serverId = ServerId("sonarr-1"))
        val otherClient = factory.clientFor(other)

        factory.invalidate(ServerId("radarr-1"))

        assertSame(otherClient, factory.clientFor(other))
    }

    @Test
    fun `config change rebuilds without explicit invalidate`() {
        val first = factory.clientFor(endpoint)
        val rotatedKey = endpoint.copy(apiKey = "rotated-key")

        assertNotSame(first, factory.clientFor(rotatedKey))
    }

    @Test
    fun `different servers get different clients`() {
        val other = endpoint.copy(serverId = ServerId("sonarr-1"))

        assertNotSame(factory.clientFor(endpoint), factory.clientFor(other))
    }

    @Test
    fun `rebuilds do not stack interceptors`() {
        factory.clientFor(endpoint)
        val second = factory.clientFor(endpoint.copy(apiKey = "other-key"))

        // Each derived client carries exactly one identity interceptor,
        // regardless of how many clients were built before it.
        assertEquals(1, second.interceptors.size)
    }

    @Test
    fun `half-filled proxy row routes direct instead of failing`() {
        server.enqueue(MockResponse().setBody("direct"))
        val brokenProxy = endpoint.copy(
            proxy = ProxyConfig(type = ProxyConfig.Type.HTTP, host = "", port = 0),
        )

        get(brokenProxy).use { response ->
            assertEquals(200, response.code)
            assertEquals("direct", response.body!!.string())
        }
    }

    @Test
    fun `explicit none proxy bypasses any ambient default`() {
        server.enqueue(MockResponse().setBody("ok"))

        get(endpoint.copy(proxy = ProxyConfig(type = ProxyConfig.Type.NONE))).use { response ->
            assertEquals(200, response.code)
        }
    }
}
