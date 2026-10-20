package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.ImportListExclusionBulkResource
import dev.appolon.homelab.arr.api.sonarr.model.ImportListExclusionResource
import dev.appolon.homelab.arr.api.sonarr.model.ImportListExclusionResourcePagingResource
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

class SonarrImportListExclusionContractTest {
    private val server = MockWebServer()
    private lateinit var api: ImportListExclusionApi

    @Before
    fun setUp() {
        server.start()
        api = SonarrDiscoveryApis.importListExclusion(
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
            MockResponse().setBody(fixture("fixtures/sonarr/importlistexclusion/list.json"))
        )
        val expected = ArrJson.decodeFromString<List<ImportListExclusionResource>>(
            fixture("fixtures/sonarr/importlistexclusion/list.json")
        )
        assertEquals(expected, api.list())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/importlistexclusion", request.requestUrl!!.encodedPath)
    }

    @Test
    fun createHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/importlistexclusion/create.json"))
        )
        val expected = ArrJson.decodeFromString<ImportListExclusionResource>(
            fixture("fixtures/sonarr/importlistexclusion/create.json")
        )
        assertEquals(
            expected, api.create(
                body = ImportListExclusionResource(id = 1, tvdbId = 12345, title = "string")
            )
        )
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/sonarr/api/v3/importlistexclusion", request.requestUrl!!.encodedPath)
        assertTrue(request.body.size > 0)
    }

    @Test
    fun pagedHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/importlistexclusion/paged.json"))
        )
        val expected = ArrJson.decodeFromString<ImportListExclusionResourcePagingResource>(
            fixture("fixtures/sonarr/importlistexclusion/paged.json")
        )
        assertEquals(
            expected, api.paged(
                page = 1, pageSize = 10, sortKey = "sample", sortDirection = SortDirection.DEFAULT
            )
        )
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/importlistexclusion/paged", request.requestUrl!!.encodedPath)
        assertEquals("1", request.requestUrl!!.queryParameter("page"))
        assertEquals("10", request.requestUrl!!.queryParameter("pageSize"))
        assertEquals("sample", request.requestUrl!!.queryParameter("sortKey"))
        assertEquals("default", request.requestUrl!!.queryParameter("sortDirection"))
    }

    @Test
    fun updateHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/importlistexclusion/update.json"))
        )
        val expected = ArrJson.decodeFromString<ImportListExclusionResource>(
            fixture("fixtures/sonarr/importlistexclusion/update.json")
        )
        assertEquals(
            expected, api.update(
                id = "1", body = ImportListExclusionResource(id = 1, tvdbId = 12345)
            )
        )
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals(
            "/sonarr/api/v3/importlistexclusion/1", request.requestUrl!!.encodedPath
        )
        assertTrue(request.body.size > 0)
    }

    @Test
    fun getHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/importlistexclusion/get.json"))
        )
        val expected = ArrJson.decodeFromString<ImportListExclusionResource>(
            fixture("fixtures/sonarr/importlistexclusion/get.json")
        )
        val response = api.get(1)
        assertEquals(200, response.code())
        assertEquals(expected, response.body())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals(
            "/sonarr/api/v3/importlistexclusion/1", request.requestUrl!!.encodedPath
        )
    }

    @Test
    fun deleteHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/importlistexclusion/delete.json"))
        )
        assertEquals(Unit, api.delete(id = 1))
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals(
            "/sonarr/api/v3/importlistexclusion/1", request.requestUrl!!.encodedPath
        )
    }

    @Test
    fun deleteBulkHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/importlistexclusion/deleteBulk.json"))
        )
        assertEquals(Unit, api.deleteBulk(body = ImportListExclusionBulkResource(ids = listOf(1))))
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals(
            "/sonarr/api/v3/importlistexclusion/bulk", request.requestUrl!!.encodedPath
        )
        assertTrue(request.body.size > 0)
    }

    @Test
    fun httpError() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        try {
            api.list()
            fail("Expected HTTP failure")
        } catch (error: HttpException) {
            assertEquals(503, error.code())
        }
    }
}
