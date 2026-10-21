package dev.appolon.homelab.arr.net

import dev.appolon.homelab.arr.core.ServerId
import java.io.IOException
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * End-to-end proxy behavior against in-process proxies and a MockWebServer
 * origin. These tests are the proof that target DNS leaves the device: the
 * SOCKS5 proxy must receive a domain-name address type, and the HTTP proxy
 * must receive a textual CONNECT authority.
 */
class ProxyRoutingTest {
    private val origin = MockWebServer()
    private val socks = Socks5ProxyServer()
    private val httpProxy = HttpConnectProxy()
    private val factory = DefaultArrClientFactory()

    @After
    fun tearDown() {
        socks.close()
        httpProxy.close()
        origin.shutdown()
    }

    private fun endpoint(proxy: ProxyConfig, trust: TrustSpec = TrustSpec.System): ServerEndpoint =
        ServerEndpoint(
            serverId = ServerId("proxy-test"),
            baseUrl = "http://localhost/",
            apiKey = "test-key",
            proxy = proxy,
            trust = trust,
        )

    @Test
    fun `socks5 proxy carries the hostname so DNS resolves on the proxy`() {
        socks.start()
        origin.enqueue(MockResponse().setBody("via-socks"))

        val proxyConfig = ProxyConfig(
            type = ProxyConfig.Type.SOCKS5,
            host = "localhost",
            port = socks.port,
        )
        val client = factory.clientFor(endpoint(proxyConfig))
        val response = client.newCall(
            Request.Builder().url(origin.url("/api/v3/health")).build(),
        ).execute()

        response.use {
            assertEquals(200, it.code)
            assertEquals("via-socks", it.body!!.string())
        }

        val target = socks.requestedTargets.single()
        assertEquals(3, target.addressType)
        assertEquals(true, target.isDomainName)
        assertEquals("localhost", target.host)
        assertEquals(origin.port, target.port)
    }

    @Test
    fun `socks5 proxy authenticates with the configured credentials`() {
        val authedSocks = Socks5ProxyServer(
            requiredUsername = "socksuser",
            requiredPassword = "sockspass",
        )
        authedSocks.start()
        origin.enqueue(MockResponse().setBody("authed-socks"))
        try {
            val client = factory.clientFor(
                endpoint(
                    ProxyConfig(
                        type = ProxyConfig.Type.SOCKS5,
                        host = "localhost",
                        port = authedSocks.port,
                        username = "socksuser",
                        password = "sockspass",
                    ),
                ),
            )
            val response = client.newCall(
                Request.Builder().url(origin.url("/api/v3/health")).build(),
            ).execute()

            response.use {
                assertEquals(200, it.code)
                assertEquals("authed-socks", it.body!!.string())
            }

            assertEquals(
                listOf("socksuser" to "sockspass"),
                authedSocks.authenticationAttempts.toList(),
            )
            assertEquals(true, authedSocks.requestedTargets.single().isDomainName)
        } finally {
            authedSocks.close()
            factory.invalidate(ServerId("proxy-test"))
        }
    }

    @Test
    fun `http proxy tunnels https with the target hostname in CONNECT`() {
        httpProxy.start()
        origin.useHttps(TestTls.localhost.sslContext.socketFactory, false)
        origin.enqueue(MockResponse().setBody("via-connect"))

        val client = factory.clientFor(
            endpoint(
                ProxyConfig(
                    type = ProxyConfig.Type.HTTP,
                    host = "localhost",
                    port = httpProxy.port,
                ),
                trust = TrustSpec.AcceptAny,
            ),
        )
        val response = client.newCall(
            Request.Builder().url(origin.url("/api/v3/health")).build(),
        ).execute()

        response.use {
            assertEquals(200, it.code)
            assertEquals("via-connect", it.body!!.string())
        }

        val authority = httpProxy.connectRequests.single().authority
        assertEquals("localhost:${origin.port}", authority)
    }

    @Test
    fun `http proxy authentication sends credentials on the tunnel`() {
        val expectedAuth = HttpConnectProxy.basicAuthorization("proxyuser", "proxypass")
        val authedProxy = HttpConnectProxy(requiredProxyAuthorization = expectedAuth)
        authedProxy.start()
        origin.useHttps(TestTls.localhost.sslContext.socketFactory, false)
        origin.enqueue(MockResponse().setBody("authed-connect"))
        try {
            val client = factory.clientFor(
                endpoint(
                    ProxyConfig(
                        type = ProxyConfig.Type.HTTP,
                        host = "localhost",
                        port = authedProxy.port,
                        username = "proxyuser",
                        password = "proxypass",
                    ),
                    trust = TrustSpec.AcceptAny,
                ),
            )
            val response = client.newCall(
                Request.Builder().url(origin.url("/api/v3/health")).build(),
            ).execute()

            response.use {
                assertEquals(200, it.code)
                assertEquals("authed-connect", it.body!!.string())
            }

            // OkHttp answers proxy challenges pre-emptively on CONNECT, so one
            // authorized request is expected; the proxy rejects anything else.
            val requests = authedProxy.connectRequests.toList()
            assertEquals(1, requests.size)
            assertEquals(expectedAuth, requests[0].proxyAuthorization)
        } finally {
            authedProxy.close()
            factory.invalidate(ServerId("proxy-test"))
        }
    }

    @Test
    fun `http proxy refuses the tunnel when credentials are wrong`() {
        val expectedAuth = HttpConnectProxy.basicAuthorization("proxyuser", "proxypass")
        val authedProxy = HttpConnectProxy(requiredProxyAuthorization = expectedAuth)
        authedProxy.start()
        origin.useHttps(TestTls.localhost.sslContext.socketFactory, false)
        try {
            val client = factory.clientFor(
                endpoint(
                    ProxyConfig(
                        type = ProxyConfig.Type.HTTP,
                        host = "localhost",
                        port = authedProxy.port,
                        username = "proxyuser",
                        password = "wrong-pass",
                    ),
                    trust = TrustSpec.AcceptAny,
                ),
            )
            assertThrows(IOException::class.java) {
                client.newCall(
                    Request.Builder().url(origin.url("/api/v3/health")).build(),
                ).execute()
            }
            assertEquals(true, authedProxy.connectRequests.isNotEmpty())
        } finally {
            authedProxy.close()
            factory.invalidate(ServerId("proxy-test"))
        }
    }
}
