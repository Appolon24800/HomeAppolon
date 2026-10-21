package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.EpisodeHistoryEventType
import dev.appolon.homelab.arr.api.sonarr.model.HistoryResource
import dev.appolon.homelab.arr.api.sonarr.model.HistoryResourcePagingResource
import dev.appolon.homelab.arr.api.sonarr.model.SortDirection
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

class SonarrHistoryContractTest {
    private val server = MockWebServer()
    private lateinit var api: HistoryApi

    @Before
    fun setUp() {
        server.start()
        api = SonarrQueueApis.history(
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
            MockResponse().setBody(fixture("fixtures/sonarr/history/list.json"))
        )
        val expected = ArrJson.decodeFromString<HistoryResourcePagingResource>(
            fixture("fixtures/sonarr/history/list.json")
        )
        val actual = api.list(
            page = 7,
            pageSize = 7,
            sortKey = "sample",
            sortDirection = SortDirection.DEFAULT,
            includeSeries = false,
            includeEpisode = false,
            eventType = listOf(7),
            episodeId = 7,
            downloadId = "sample",
            seriesIds = listOf(7),
            languages = listOf(7),
            quality = listOf(7)
        )
        assertEquals(expected, actual)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/history", request.requestUrl!!.encodedPath)
        assertEquals("7", request.requestUrl!!.queryParameter("page"))
        assertEquals("7", request.requestUrl!!.queryParameter("pageSize"))
        assertEquals("sample", request.requestUrl!!.queryParameter("sortKey"))
        assertEquals("default", request.requestUrl!!.queryParameter("sortDirection"))
        assertEquals("false", request.requestUrl!!.queryParameter("includeSeries"))
        assertEquals("false", request.requestUrl!!.queryParameter("includeEpisode"))
        assertEquals("7", request.requestUrl!!.queryParameter("eventType"))
        assertEquals("7", request.requestUrl!!.queryParameter("episodeId"))
        assertEquals("sample", request.requestUrl!!.queryParameter("downloadId"))
        assertEquals("7", request.requestUrl!!.queryParameter("seriesIds"))
        assertEquals("7", request.requestUrl!!.queryParameter("languages"))
        assertEquals("7", request.requestUrl!!.queryParameter("quality"))

    }

    @Test
    fun sinceHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/history/since.json"))
        )
        val expected = ArrJson.decodeFromString<List<HistoryResource>>(
            fixture("fixtures/sonarr/history/since.json")
        )
        val actual = api.since(
            date = "sample",
            eventType = EpisodeHistoryEventType.UNKNOWN,
            includeSeries = false,
            includeEpisode = false
        )
        assertEquals(expected, actual)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/history/since", request.requestUrl!!.encodedPath)
        assertEquals("sample", request.requestUrl!!.queryParameter("date"))
        assertEquals("unknown", request.requestUrl!!.queryParameter("eventType"))
        assertEquals("false", request.requestUrl!!.queryParameter("includeSeries"))
        assertEquals("false", request.requestUrl!!.queryParameter("includeEpisode"))

    }

    @Test
    fun seriesHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/history/series.json"))
        )
        val expected = ArrJson.decodeFromString<List<HistoryResource>>(
            fixture("fixtures/sonarr/history/series.json")
        )
        val actual = api.series(
            seriesId = 7,
            seasonNumber = 7,
            eventType = EpisodeHistoryEventType.UNKNOWN,
            includeSeries = false,
            includeEpisode = false
        )
        assertEquals(expected, actual)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/history/series", request.requestUrl!!.encodedPath)
        assertEquals("7", request.requestUrl!!.queryParameter("seriesId"))
        assertEquals("7", request.requestUrl!!.queryParameter("seasonNumber"))
        assertEquals("unknown", request.requestUrl!!.queryParameter("eventType"))
        assertEquals("false", request.requestUrl!!.queryParameter("includeSeries"))
        assertEquals("false", request.requestUrl!!.queryParameter("includeEpisode"))

    }

    @Test
    fun markFailedHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/history/markFailed.json"))
        )
        assertEquals(Unit, api.markFailed(id = 7))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/sonarr/api/v3/history/failed/7", request.requestUrl!!.encodedPath)


    }

    @Test
    fun httpError() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        try {
            api.list(
                page = null,
                pageSize = null,
                sortKey = null,
                sortDirection = null,
                includeSeries = null,
                includeEpisode = null,
                eventType = null,
                episodeId = null,
                downloadId = null,
                seriesIds = null,
                languages = null,
                quality = null
            )
            fail("Expected HTTP failure")
        } catch (error: HttpException) {
            assertEquals(503, error.code())
        }
    }
}
