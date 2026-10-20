package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.BlocklistBulkResource
import dev.appolon.homelab.arr.api.sonarr.model.BlocklistResourcePagingResource
import dev.appolon.homelab.arr.api.sonarr.model.DownloadProtocol
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

class SonarrBlocklistContractTest {
    private val server = MockWebServer()
    private lateinit var api: BlocklistApi

    @Before
    fun setUp() {
        server.start()
        api = SonarrQueueApis.blocklist(
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
            MockResponse().setBody(fixture("fixtures/sonarr/blocklist/list.json"))
        )
        val expected = ArrJson.decodeFromString<BlocklistResourcePagingResource>(
            fixture("fixtures/sonarr/blocklist/list.json")
        )
        val actual = api.list(
            page = 7,
            pageSize = 7,
            sortKey = "sample",
            sortDirection = SortDirection.DEFAULT,
            seriesIds = listOf(7),
            protocols = listOf(DownloadProtocol.UNKNOWN)
        )
        assertEquals(expected, actual)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/blocklist", request.requestUrl!!.encodedPath)
        assertEquals("7", request.requestUrl!!.queryParameter("page"))
        assertEquals("7", request.requestUrl!!.queryParameter("pageSize"))
        assertEquals("sample", request.requestUrl!!.queryParameter("sortKey"))
        assertEquals("default", request.requestUrl!!.queryParameter("sortDirection"))
        assertEquals("7", request.requestUrl!!.queryParameter("seriesIds"))
        assertEquals("unknown", request.requestUrl!!.queryParameter("protocols"))

    }

    @Test
    fun deleteHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/blocklist/delete.json"))
        )
        assertEquals(Unit, api.delete(id = 7))
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/sonarr/api/v3/blocklist/7", request.requestUrl!!.encodedPath)


    }

    @Test
    fun deleteBulkHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/blocklist/deleteBulk.json"))
        )
        assertEquals(Unit, api.deleteBulk(body = BlocklistBulkResource()))
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/sonarr/api/v3/blocklist/bulk", request.requestUrl!!.encodedPath)

        assertTrue(request.body.size > 0)
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
                seriesIds = null,
                protocols = null
            )
            fail("Expected HTTP failure")
        } catch (error: HttpException) {
            assertEquals(503, error.code())
        }
    }
}
