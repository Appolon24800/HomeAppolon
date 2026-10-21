package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.QueueResource
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class SonarrQueueDetailsContractTest {
    private val server = MockWebServer()
    private lateinit var api: QueueDetailsApi

    @Before
    fun setUp() {
        server.start()
        api = SonarrQueueApis.queueDetails(
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
            MockResponse().setBody(fixture("fixtures/sonarr/queuedetails/list.json"))
        )
        val expected = ArrJson.decodeFromString<List<QueueResource>>(
            fixture("fixtures/sonarr/queuedetails/list.json")
        )
        val actual = api.list(
            seriesId = 7,
            episodeIds = listOf(7),
            includeSeries = false,
            includeEpisode = false
        )
        assertEquals(expected, actual)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/queue/details", request.requestUrl!!.encodedPath)
        assertEquals("7", request.requestUrl!!.queryParameter("seriesId"))
        assertEquals("7", request.requestUrl!!.queryParameter("episodeIds"))
        assertEquals("false", request.requestUrl!!.queryParameter("includeSeries"))
        assertEquals("false", request.requestUrl!!.queryParameter("includeEpisode"))

    }

    @Test
    fun httpError() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        try {
            api.list(
                seriesId = null,
                episodeIds = null,
                includeSeries = null,
                includeEpisode = null
            )
            fail("Expected HTTP failure")
        } catch (error: HttpException) {
            assertEquals(503, error.code())
        }
    }
}
