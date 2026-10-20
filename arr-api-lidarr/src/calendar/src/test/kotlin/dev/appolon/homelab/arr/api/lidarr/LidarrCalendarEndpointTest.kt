package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

internal fun calendarFixture(path: String): String = checkNotNull(
    LidarrCalendarEndpointTest::class.java.getResourceAsStream("/fixtures/lidarr/$path")
).bufferedReader().use { it.readText() }

internal inline fun <reified T> calendarDto(path: String): T =
    ArrJson.decodeFromString(calendarFixture(path))

internal fun calendarRetrofit(server: MockWebServer): Retrofit = Retrofit.Builder()
    .baseUrl(server.url("/lidarr/"))
    .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
    .build()

/** Each row is one spec operation; the feed serves the fixture bytes verbatim. */
data class CalendarEndpointCase(
    val name: String,
    val method: String,
    val path: String,
    val response: String = "",
    val queries: Map<String, List<String>> = emptyMap(),
    val body: JsonElement? = null,
    val call: suspend (Retrofit) -> Any?,
) {
    override fun toString(): String = name
}

@RunWith(Parameterized::class)
class LidarrCalendarEndpointTest(private val case: CalendarEndpointCase) {
    private val server = MockWebServer()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun happyPath() = runTest {
        server.enqueue(MockResponse().setBody(case.response))
        case.call(calendarRetrofit(server))
        val request = server.takeRequest()
        assertEquals(case.method, request.method)
        assertEquals("/lidarr/${case.path}", request.requestUrl!!.encodedPath)
        assertEquals(case.queries.keys, request.requestUrl!!.queryParameterNames)
        case.queries.forEach { (name, values) ->
            assertEquals(values, request.requestUrl!!.queryParameterValues(name))
        }
        if (case.body != null) {
            assertEquals(case.body, ArrJson.parseToJsonElement(request.body.readUtf8()))
            assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
        } else {
            assertEquals(0L, request.bodySize)
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun operations(): List<Array<Any>> {
            val albums = calendarDto<List<AlbumResource>>("calendar/getCalendar.json")
            val album = calendarDto<AlbumResource>("calendar/getCalendarById.json")
            val feed = calendarFixture("calendarfeed/getCalendarFeed.ics")
            fun query(vararg pairs: Pair<String, String>): Map<String, List<String>> =
                pairs.groupBy({ it.first }, { it.second })
            return listOf(
                CalendarEndpointCase(
                    "getCalendar", "GET", "api/v1/calendar",
                    calendarFixture("calendar/getCalendar.json"),
                    query(
                        "start" to "2026-10-01T00:00:00Z", "end" to "2026-12-31T00:00:00Z",
                        "unmonitored" to "true", "includeArtist" to "true", "tags" to "4",
                    ),
                ) { r ->
                    assertEquals(albums, LidarrCalendarApis.calendar(r).getCalendar(
                        "2026-10-01T00:00:00Z", "2026-12-31T00:00:00Z", true, true, "4",
                    ))
                },
                CalendarEndpointCase(
                    "getCalendarById", "GET", "api/v1/calendar/1",
                    calendarFixture("calendar/getCalendarById.json"),
                ) { r -> assertEquals(album, LidarrCalendarApis.calendar(r).getCalendarById(1)) },
                CalendarEndpointCase(
                    "getCalendarFeed", "GET", "feed/v1/calendar/lidarr.ics", feed,
                    query(
                        "pastDays" to "3", "futureDays" to "31",
                        "tags" to "4", "unmonitored" to "true",
                    ),
                ) { r ->
                    assertEquals(feed, LidarrCalendarApis.calendarFeed(r)
                        .getCalendarFeed(3, 31, "4", true).string())
                },
            ).map { arrayOf(it) }
        }
    }
}
