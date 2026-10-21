package dev.appolon.homelab.arr.net

import dev.appolon.homelab.arr.core.ArrError
import java.io.IOException
import java.net.ServerSocket
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrHttpErrorTest {
    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun execute(code: Int, body: String? = null): okhttp3.Response {
        server.enqueue(
            MockResponse().setResponseCode(code).apply {
                if (body != null) setBody(body)
            },
        )
        return Request.Builder().url(server.url("/api/v3/missing")).build().let {
            okhttp3.OkHttpClient().newCall(it).execute()
        }
    }

    @Test
    fun `401 maps to auth error`() {
        execute(401).use { response ->
            assertEquals(ArrError.Auth(401), response.arrError())
        }
    }

    @Test
    fun `403 maps to auth error`() {
        execute(403).use { response ->
            assertEquals(ArrError.Auth(403), response.arrError())
        }
    }

    @Test
    fun `other non-2xx maps to http error with body snippet`() {
        execute(500, "{\"message\":\"boom\"}").use { response ->
            assertEquals(ArrError.Http(500, "{\"message\":\"boom\"}"), response.arrError())
        }
    }

    @Test
    fun `404 maps to http error`() {
        execute(404).use { response ->
            assertEquals(ArrError.Http(404, null), response.arrError())
        }
    }

    @Test
    fun `body snippet is capped`() {
        val huge = "x".repeat(10_000)

        execute(503, huge).use { response ->
            val error = response.arrError() as ArrError.Http

            assertTrue(error.bodySnippet!!.length <= 2048)
        }
    }

    @Test
    fun `blank body yields a null snippet`() {
        execute(502, "   ").use { response ->
            assertEquals(ArrError.Http(502, null), response.arrError())
        }
    }

    @Test
    fun `io failures map to network error`() {
        // Grab a port and close it so the connect is refused immediately.
        val socket = ServerSocket(0)
        val port = socket.localPort
        socket.close()
        val request = Request.Builder().url("http://127.0.0.1:$port/").build()

        val thrown = try {
            okhttp3.OkHttpClient().newCall(request).execute()
            error("Expected the refused connection to throw")
        } catch (e: IOException) {
            e
        }

        assertTrue(thrown.arrError() is ArrError.Network)
    }

    @Test
    fun `non-io failures map to unknown error`() {
        val error = IllegalStateException("parser blew up").arrError()

        assertTrue(error is ArrError.Unknown)
    }
}
