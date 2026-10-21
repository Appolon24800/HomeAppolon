package dev.appolon.homelab.arr.servers.net

import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.net.ServerEndpoint
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerConnectionTesterTest {

    private val tester = ServerConnectionTester()

    private fun endpoint(baseUrl: String) = ServerEndpoint(
        serverId = ServerId("test"),
        baseUrl = baseUrl,
        apiKey = "key123",
    )

    @Test
    fun `success reports the version and stamps the api key header`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody("""{"version":"4.9.3.9999","appName":"Sonarr"}"""),
        )
        server.start()
        try {
            val outcome = tester.test(endpoint(server.url("/").toString()), AppType.SONARR)
            assertEquals(ConnectionOutcome.Success("4.9.3.9999"), outcome)
            val recorded = server.takeRequest()
            assertEquals("/api/v3/system/status", recorded.path)
            assertEquals("key123", recorded.getHeader("X-Api-Key"))
            assertEquals("GET", recorded.method)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `status paths follow the app type`() = runTest {
        mapOf(
            AppType.RADARR to "/api/v3/system/status",
            AppType.LIDARR to "/api/v1/system/status",
            AppType.PROWLARR to "/api/v1/system/status",
            AppType.BAZARR to "/api/system/status",
        ).forEach { (appType, expectedPath) ->
            val server = MockWebServer()
            server.enqueue(MockResponse().setBody("""{"version":"1.0"}"""))
            server.start()
            try {
                tester.test(endpoint(server.url("/").toString()), appType)
                assertEquals(expectedPath, server.takeRequest().path)
            } finally {
                server.shutdown()
            }
        }
    }

    @Test
    fun `auth failures map to ArrError-Auth`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        server.start()
        try {
            val outcome = tester.test(endpoint(server.url("/").toString()), AppType.RADARR)
            assertTrue(outcome is ConnectionOutcome.Failure)
            assertEquals(ArrError.Auth(401), (outcome as ConnectionOutcome.Failure).error)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `server errors map to ArrError-Http`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        server.start()
        try {
            val outcome = tester.test(endpoint(server.url("/").toString()), AppType.RADARR)
            val error = (outcome as ConnectionOutcome.Failure).error
            assertTrue(error is ArrError.Http)
            assertEquals(500, (error as ArrError.Http).code)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `versionless but reachable servers still succeed`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"appName":"Radarr"}"""))
        server.start()
        try {
            val outcome = tester.test(endpoint(server.url("/").toString()), AppType.RADARR)
            assertEquals(ConnectionOutcome.Success(null), outcome)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `unreachable hosts map to ArrError-Network`() = runTest {
        val outcome = tester.test(endpoint("http://127.0.0.1:9"), AppType.RADARR)
        val error = (outcome as ConnectionOutcome.Failure).error
        assertTrue("expected Network, got $error", error is ArrError.Network)
    }
}
