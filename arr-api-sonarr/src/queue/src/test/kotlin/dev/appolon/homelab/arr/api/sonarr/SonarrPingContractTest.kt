package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.PingResource
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class SonarrPingContractTest {
    private val server = MockWebServer()
    private lateinit var api: PingApi

    @Before
    fun setUp() {
        server.start()
        api = SonarrQueueApis.ping(
            Retrofit.Builder().baseUrl(server.url("/sonarr/"))
                .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
                .build()
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun fixture(path: String): String =
        checkNotNull(javaClass.classLoader!!.getResource(path)).readText()

    @Test
    fun listHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/ping/list.json"))
        )
        val expected = ArrJson.decodeFromString<PingResource>(
            fixture("fixtures/sonarr/ping/list.json")
        )
        val actual = api.list()
        assertEquals(200, actual.code())
        assertEquals(expected, actual.body())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/ping", request.requestUrl!!.encodedPath)


    }

    @Test
    fun headHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/ping/head.json"))
        )
        assertEquals(200, api.head().code())
        val request = server.takeRequest()
        assertEquals("HEAD", request.method)
        assertEquals("/sonarr/ping", request.requestUrl!!.encodedPath)


    }

    @Test
    fun httpError() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        assertEquals(503, api.list().code())
    }
}
