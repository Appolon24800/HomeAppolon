package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class SonarrCalendarContractTest {
    private val server = MockWebServer()
    private lateinit var api: CalendarApi

    @Before
    fun setUp() {
        server.start()
        api = SonarrCalendarApis.calendar(
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
            MockResponse().setBody(fixture("fixtures/sonarr/calendar/list.json"))
        )
        val expected = ArrJson.decodeFromString<List<EpisodeResource>>(
            fixture("fixtures/sonarr/calendar/list.json")
        )
        val actual = api.list(
            start = "2026-01-10T00:00:00Z",
            end = "2026-01-20T00:00:00Z",
            unmonitored = false,
            includeSeries = true,
            includeEpisodeFile = false,
            includeEpisodeImages = false,
            tags = ""
        )
        assertEquals(expected, actual)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/calendar", request.requestUrl!!.encodedPath)
        assertEquals("2026-01-10T00:00:00Z", request.requestUrl!!.queryParameter("start"))
        assertEquals("2026-01-20T00:00:00Z", request.requestUrl!!.queryParameter("end"))
        assertEquals("false", request.requestUrl!!.queryParameter("unmonitored"))
        assertEquals("true", request.requestUrl!!.queryParameter("includeSeries"))
    }

    @Test
    fun getHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/calendar/get.json"))
        )
        val expected = ArrJson.decodeFromString<EpisodeResource>(
            fixture("fixtures/sonarr/calendar/get.json")
        )
        val actual = api.get(id = 2)
        assertEquals(expected, actual.body())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/calendar/2", request.requestUrl!!.encodedPath)
    }

    @Test
    fun getNotFound() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("{}"))
        val actual = api.get(id = 99)
        assertFalse(actual.isSuccessful)
        assertEquals(404, actual.code())
        server.takeRequest()
    }

    @Test
    fun httpError() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        try {
            api.list(
                start = null,
                end = null,
                unmonitored = null,
                includeSeries = null,
                includeEpisodeFile = null,
                includeEpisodeImages = null,
                tags = null
            )
            fail("Expected HTTP failure")
        } catch (error: HttpException) {
            assertEquals(503, error.code())
        }
    }
}
