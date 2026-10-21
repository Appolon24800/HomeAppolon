package dev.appolon.homelab.arr.net

import dev.appolon.homelab.arr.core.ServerId
import java.net.InetSocketAddress
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class DefaultArrClientFactoryTest {
    private val server = MockWebServer()
    private val factory = DefaultArrClientFactory()

    /** Base URL shares the mock server's origin so same-origin auth applies. */
    private val endpoint: ServerEndpoint
        get() = ServerEndpoint(
            serverId = ServerId("radarr-1"),
            baseUrl = server.url("/").toString(),
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
    fun `sends the api key and user agent on same-origin requests`() {
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
    fun `third-party hosts never receive credentials`() {
        val artwork = MockWebServer()
        artwork.start()
        try {
            artwork.enqueue(MockResponse().setBody("image"))
            val client = factory.clientFor(endpoint)

            client.newCall(Request.Builder().url(artwork.url("/poster.jpg")).build())
                .execute()
                .use { response -> assertEquals(200, response.code) }

            val recorded = artwork.takeRequest()
            assertNull(recorded.getHeader("X-Api-Key"))
            assertNull(recorded.getHeader("Authorization"))
            assertEquals("HomelabArr/1.0 (android)", recorded.getHeader("User-Agent"))
        } finally {
            artwork.shutdown()
        }
    }

    @Test
    fun `cross-origin redirect strips the credentials`() {
        val elsewhere = MockWebServer()
        elsewhere.start()
        try {
            elsewhere.enqueue(MockResponse().setBody("moved"))
            server.enqueue(
                MockResponse().setResponseCode(302)
                    .setHeader("Location", elsewhere.url("/leak.jpg")),
            )

            get(endpoint.copy(basicAuthUser = "media", basicAuthPassword = "homelab")).use { response ->
                assertEquals(200, response.code)
                assertEquals("moved", response.body!!.string())
            }

            server.takeRequest() // the original same-origin hop
            val redirected = elsewhere.takeRequest()
            assertNull(redirected.getHeader("X-Api-Key"))
            assertNull(redirected.getHeader("Authorization"))
        } finally {
            elsewhere.shutdown()
        }
    }

    @Test
    fun `same-origin redirect keeps the credentials`() {
        server.enqueue(
            MockResponse().setResponseCode(302)
                .setHeader("Location", server.url("/api/v3/health")),
        )
        server.enqueue(MockResponse().setBody("ok"))

        get(endpoint).use { response ->
            assertEquals(200, response.code)
        }

        server.takeRequest()
        val followUp = server.takeRequest()
        assertEquals("test-key", followUp.getHeader("X-Api-Key"))
    }

    @Test
    fun `image client carries no credentials`() {
        server.enqueue(MockResponse().setBody("img"))

        factory.imageClientFor(endpoint)
            .newCall(Request.Builder().url(url()).build())
            .execute()
            .use { response -> assertEquals(200, response.code) }

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("X-Api-Key"))
        assertNull(recorded.getHeader("Authorization"))
        assertEquals("HomelabArr/1.0 (android)", recorded.getHeader("User-Agent"))
    }

    @Test
    fun `image clients cache separately from authenticated clients`() {
        assertNotSame(factory.imageClientFor(endpoint), factory.clientFor(endpoint))
        assertSame(factory.imageClientFor(endpoint), factory.imageClientFor(endpoint))
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
    fun `invalidate drops the image client too`() {
        val first = factory.imageClientFor(endpoint)
        factory.invalidate(ServerId("radarr-1"))

        assertNotSame(first, factory.imageClientFor(endpoint))
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
        assertEquals(2, second.networkInterceptors.size)
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

    @Test
    fun `global default proxy applies when the server has none`() {
        val withGlobal = DefaultArrClientFactory(
            globalProxy = { ProxyConfig(type = ProxyConfig.Type.HTTP, host = "global-proxy", port = 3128) },
        )

        val client = withGlobal.clientFor(endpoint)

        assertEquals(
            InetSocketAddress.createUnresolved("global-proxy", 3128),
            client.proxy!!.address(),
        )
    }

    @Test
    fun `server proxy override wins over the global default`() {
        val withGlobal = DefaultArrClientFactory(
            globalProxy = { ProxyConfig(type = ProxyConfig.Type.HTTP, host = "global-proxy", port = 3128) },
        )

        val client = withGlobal.clientFor(
            endpoint.copy(proxy = ProxyConfig(type = ProxyConfig.Type.HTTP, host = "server-proxy", port = 8888)),
        )

        assertEquals(
            InetSocketAddress.createUnresolved("server-proxy", 8888),
            client.proxy!!.address(),
        )
    }

    @Test
    fun `global default change rebuilds the cached client`() {
        var host = "one"
        val withGlobal = DefaultArrClientFactory(
            globalProxy = { ProxyConfig(type = ProxyConfig.Type.HTTP, host = host, port = 3128) },
        )
        val first = withGlobal.clientFor(endpoint)

        host = "two"

        assertNotSame(first, withGlobal.clientFor(endpoint))
    }

    @Test
    fun `useOrbot without Orbot installed fails closed`() {
        assertThrows(OrbotUnavailableException::class.java) {
            factory.clientFor(endpoint.copy(proxy = ProxyConfig(useOrbot = true)))
        }
    }
}
