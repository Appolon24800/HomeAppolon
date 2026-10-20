package dev.appolon.homelab.arr.api.bazarr

import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** The webhook probe carries its outcome in the HTTP status; the body carries no schema. */
class BazarrSystemSettingsApiTest {
    @Test
    fun happyPath() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val fixture = requireNotNull(javaClass.getResource(
                "/fixtures/bazarr/system-settings/response.json"
            )).readText()
            server.enqueue(MockResponse().setBody(fixture))
            val response = BazarrSystemApis.settings(retrofit(server)).postSystemWebhookTest()
            assertEquals(200, response.code())
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/prefix/api/system/webhooks/test", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun errorPath() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
            val response = BazarrSystemApis.settings(retrofit(server)).postSystemWebhookTest()
            assertEquals(401, response.code())
        } finally {
            server.shutdown()
        }
    }

    private fun retrofit(server: MockWebServer): Retrofit = Retrofit.Builder()
        .baseUrl(server.url("/prefix/api/"))
        .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
        .build()
}
