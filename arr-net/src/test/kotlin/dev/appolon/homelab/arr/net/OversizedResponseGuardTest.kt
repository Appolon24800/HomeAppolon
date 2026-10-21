package dev.appolon.homelab.arr.net

import dev.appolon.homelab.arr.core.ServerId
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OversizedResponseGuardTest {

    @Test
    fun `a response larger than the limit fails as a network error`() {
        val server = MockWebServer()
        val huge = "x".repeat((OversizedResponseGuard.MAX_BYTES + 1024).toInt())
        server.enqueue(MockResponse().setBody(huge))
        server.start()
        try {
            val client = OkHttpClient.Builder()
                .addInterceptor(OversizedResponseGuard)
                .build()
            try {
                client.newCall(Request.Builder().url(server.url("/api/v1/album")).build())
                    .execute()
                fail("the guarded call must not succeed")
            } catch (e: IOException) {
                assertTrue(e.message!!.contains("the limit is"))
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `normal sized responses pass through untouched`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        server.start()
        try {
            val client = OkHttpClient.Builder()
                .addInterceptor(OversizedResponseGuard)
                .build()
            client.newCall(Request.Builder().url(server.url("/api/v1/artist")).build())
                .execute().use { response ->
                    assertTrue(response.isSuccessful)
                    assertTrue(response.body!!.string().contains("ok"))
                }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `the factory installs the guard on its clients`() {
        val server = MockWebServer()
        val huge = "x".repeat((OversizedResponseGuard.MAX_BYTES + 1024).toInt())
        server.enqueue(MockResponse().setBody(huge))
        server.start()
        try {
            val factory = DefaultArrClientFactory()
            val endpoint = ServerEndpoint(ServerId("guard"), server.url("/").toString(), "key")
            try {
                factory.clientFor(endpoint).newCall(
                    Request.Builder().url(server.url("/api/v1/album")).build(),
                ).execute()
                fail("the guarded call must not succeed")
            } catch (e: IOException) {
                assertTrue(e.message!!.contains("the limit is"))
            }
        } finally {
            server.shutdown()
        }
    }
}
