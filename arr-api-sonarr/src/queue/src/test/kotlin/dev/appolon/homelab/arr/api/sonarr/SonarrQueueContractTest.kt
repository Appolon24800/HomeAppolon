package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.sonarr.model.QueueBulkResource
import dev.appolon.homelab.arr.api.sonarr.model.QueueResourcePagingResource
import dev.appolon.homelab.arr.api.sonarr.model.QueueStatus
import dev.appolon.homelab.arr.api.sonarr.model.SortDirection
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class SonarrQueueContractTest {
    private val server = MockWebServer()
    private lateinit var api: QueueApi

    @Before
    fun setUp() {
        server.start()
        api = SonarrQueueApis.queue(
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
    fun deleteHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/queue/delete.json"))
        )
        assertEquals(Unit, api.delete(
            id = 7,
            removeFromClient = false,
            blocklist = false,
            skipRedownload = false,
            changeCategory = false
        ))
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/sonarr/api/v3/queue/7", request.requestUrl!!.encodedPath)
        assertEquals("false", request.requestUrl!!.queryParameter("removeFromClient"))
        assertEquals("false", request.requestUrl!!.queryParameter("blocklist"))
        assertEquals("false", request.requestUrl!!.queryParameter("skipRedownload"))
        assertEquals("false", request.requestUrl!!.queryParameter("changeCategory"))

    }

    @Test
    fun deleteBulkHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/queue/deleteBulk.json"))
        )
        assertEquals(Unit, api.deleteBulk(
            removeFromClient = false,
            blocklist = false,
            skipRedownload = false,
            changeCategory = false,
            body = QueueBulkResource()
        ))
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/sonarr/api/v3/queue/bulk", request.requestUrl!!.encodedPath)
        assertEquals("false", request.requestUrl!!.queryParameter("removeFromClient"))
        assertEquals("false", request.requestUrl!!.queryParameter("blocklist"))
        assertEquals("false", request.requestUrl!!.queryParameter("skipRedownload"))
        assertEquals("false", request.requestUrl!!.queryParameter("changeCategory"))
        assertTrue(request.body.size > 0)
    }

    @Test
    fun listHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/queue/list.json"))
        )
        val expected = ArrJson.decodeFromString<QueueResourcePagingResource>(
            fixture("fixtures/sonarr/queue/list.json")
        )
        val actual = api.list(
            page = 7,
            pageSize = 7,
            sortKey = "sample",
            sortDirection = SortDirection.DEFAULT,
            includeUnknownSeriesItems = false,
            includeSeries = false,
            includeEpisode = false,
            seriesIds = listOf(7),
            protocol = DownloadProtocol.UNKNOWN,
            languages = listOf(7),
            quality = listOf(7),
            status = listOf(QueueStatus.UNKNOWN)
        )
        assertEquals(expected, actual)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/queue", request.requestUrl!!.encodedPath)
        assertEquals("7", request.requestUrl!!.queryParameter("page"))
        assertEquals("7", request.requestUrl!!.queryParameter("pageSize"))
        assertEquals("sample", request.requestUrl!!.queryParameter("sortKey"))
        assertEquals("default", request.requestUrl!!.queryParameter("sortDirection"))
        assertEquals("false", request.requestUrl!!.queryParameter("includeUnknownSeriesItems"))
        assertEquals("false", request.requestUrl!!.queryParameter("includeSeries"))
        assertEquals("false", request.requestUrl!!.queryParameter("includeEpisode"))
        assertEquals("7", request.requestUrl!!.queryParameter("seriesIds"))
        assertEquals("unknown", request.requestUrl!!.queryParameter("protocol"))
        assertEquals("7", request.requestUrl!!.queryParameter("languages"))
        assertEquals("7", request.requestUrl!!.queryParameter("quality"))
        assertEquals("unknown", request.requestUrl!!.queryParameter("status"))

    }

    @Test
    fun httpError() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        try {
            api.delete(
                id = 0,
                removeFromClient = null,
                blocklist = null,
                skipRedownload = null,
                changeCategory = null
            )
            fail("Expected HTTP failure")
        } catch (error: HttpException) {
            assertEquals(503, error.code())
        }
    }
}
