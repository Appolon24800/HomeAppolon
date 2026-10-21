package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class SonarrCalendarFeedContractTest {
    private val server = MockWebServer()
    private lateinit var api: CalendarFeedApi

    @Before
    fun setUp() {
        server.start()
        api = SonarrCalendarApis.calendarFeed(
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
        val ics = fixture("fixtures/sonarr/calendarfeed/feed.ics")
        server.enqueue(
            MockResponse().setBody(ics).setHeader("Content-Type", "text/calendar")
        )
        val actual = api.list(
            pastDays = 3,
            futureDays = 14,
            tags = "",
            unmonitored = true,
            premieresOnly = false,
            asAllDay = false
        )
        assertEquals(ics, actual.body()!!.string())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/feed/v3/calendar/sonarr.ics", request.requestUrl!!.encodedPath)
        assertEquals("3", request.requestUrl!!.queryParameter("pastDays"))
        assertEquals("14", request.requestUrl!!.queryParameter("futureDays"))
        assertEquals("true", request.requestUrl!!.queryParameter("unmonitored"))
    }

    @Test
    fun httpError() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))
        val actual = api.list(
            pastDays = null,
            futureDays = null,
            tags = null,
            unmonitored = null,
            premieresOnly = null,
            asAllDay = null
        )
        assertFalse(actual.isSuccessful)
        assertEquals(503, actual.code())
        assertEquals("unavailable", actual.errorBody()!!.string())
    }
}
