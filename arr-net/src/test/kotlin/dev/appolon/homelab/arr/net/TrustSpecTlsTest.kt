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
 * Full TLS handshakes against a MockWebServer presenting test-only self-signed
 * certificates. The `mismatch` certificate is intentionally not valid for
 * "localhost", so a pinned connection only succeeds when hostname verification
 * really is bypassed for the pin.
 */
class TrustSpecTlsTest {
    private val server = MockWebServer()
    private val factory = DefaultArrClientFactory()

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun endpoint(trust: TrustSpec): ServerEndpoint = ServerEndpoint(
        serverId = ServerId("tls-test"),
        baseUrl = "https://localhost/",
        apiKey = "test-key",
        trust = trust,
    )

    private fun get(trust: TrustSpec, path: String = "/api/v3/health"): okhttp3.Response =
        factory.clientFor(endpoint(trust)).newCall(
            Request.Builder().url(server.url(path)).build(),
        ).execute()

    @Test
    fun `system trust rejects a self-signed certificate`() {
        server.useHttps(TestTls.localhost.sslContext.socketFactory, false)

        assertThrows(IOException::class.java) { get(TrustSpec.System).close() }
    }

    @Test
    fun `accept any trusts a self-signed certificate for its own hostname`() {
        server.useHttps(TestTls.localhost.sslContext.socketFactory, false)
        server.enqueue(MockResponse().setBody("trusted"))

        get(TrustSpec.AcceptAny).use { response ->
            assertEquals(200, response.code)
            assertEquals("trusted", response.body!!.string())
        }
    }

    @Test
    fun `pinned accepts a certificate whose hostname does not match`() {
        server.useHttps(TestTls.mismatch.sslContext.socketFactory, false)
        server.enqueue(MockResponse().setBody("pinned"))

        get(TrustSpec.Pinned(TestTls.mismatch.sha256FingerprintHex)).use { response ->
            assertEquals(200, response.code)
            assertEquals("pinned", response.body!!.string())
        }
    }

    @Test
    fun `pinned rejects a certificate with a different fingerprint`() {
        server.useHttps(TestTls.mismatch.sslContext.socketFactory, false)

        val wrongPin = TrustSpec.Pinned(TestTls.localhost.sha256FingerprintHex)

        assertThrows(IOException::class.java) { get(wrongPin).close() }
    }

    @Test
    fun `pinned fingerprints tolerate colons and uppercase`() {
        server.useHttps(TestTls.mismatch.sslContext.socketFactory, false)
        server.enqueue(MockResponse().setBody("formatted"))

        val formatted = TestTls.mismatch.sha256FingerprintHex
            .chunked(2)
            .joinToString(":")
            .uppercase()

        get(TrustSpec.Pinned(formatted)).use { response ->
            assertEquals(200, response.code)
        }
    }

    @Test
    fun `accept any still enforces hostname verification`() {
        // The mismatch certificate is not valid for localhost, so even with the
        // chain accepted the hostname check must fail.
        server.useHttps(TestTls.mismatch.sslContext.socketFactory, false)

        assertThrows(IOException::class.java) { get(TrustSpec.AcceptAny).close() }
    }

    @Test
    fun `fingerprint normalization strips separators and case`() {
        val raw = "AB:CD EF:01"

        assertEquals("abcdef01", normalizeFingerprint(raw))
    }
}
