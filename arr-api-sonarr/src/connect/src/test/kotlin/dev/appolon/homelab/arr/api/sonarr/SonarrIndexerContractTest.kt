package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.IndexerBulkResource
import dev.appolon.homelab.arr.api.sonarr.model.IndexerResource
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

@Suppress("DEPRECATION")
class SonarrIndexerContractTest {
    private val server = MockWebServer()
    private lateinit var api: IndexerApi

    @Before
    fun setUp() {
        server.start()
        api = SonarrConnectApis.indexer(
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
            MockResponse().setBody(fixture("fixtures/sonarr/indexer/list.json"))
        )
        val expected = ArrJson.decodeFromString<List<IndexerResource>>(
            fixture("fixtures/sonarr/indexer/list.json")
        )
        val result = api.list()
        assertEquals(expected, result)
        assertEquals(2, result.size)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/indexer", request.requestUrl!!.encodedPath)
    }

    @Test
    fun createHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/indexer/create.json"))
        )
        val expected = ArrJson.decodeFromString<IndexerResource>(
            fixture("fixtures/sonarr/indexer/create.json")
        )
        assertEquals(
            expected,
            api.create(
                forceSave = false,
                body = IndexerResource(name = "NZBgeek", implementation = "Newznab")
            )
        )
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/sonarr/api/v3/indexer", request.requestUrl!!.encodedPath)
        assertEquals("false", request.requestUrl!!.queryParameter("forceSave"))
        assertTrue(request.body.size > 0)
    }

    @Test
    fun updateHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/indexer/update.json"))
        )
        val expected = ArrJson.decodeFromString<IndexerResource>(
            fixture("fixtures/sonarr/indexer/update.json")
        )
        assertEquals(
            expected, api.update(
                id = 1, forceSave = false, body = IndexerResource(id = 1, name = "NZBgeek")
            )
        )
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/sonarr/api/v3/indexer/1", request.requestUrl!!.encodedPath)
        assertEquals("false", request.requestUrl!!.queryParameter("forceSave"))
    }

    @Test
    fun getHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/indexer/get.json"))
        )
        val expected = ArrJson.decodeFromString<IndexerResource>(
            fixture("fixtures/sonarr/indexer/get.json")
        )
        val response = api.get(1)
        assertEquals(200, response.code())
        assertEquals(expected, response.body())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/indexer/1", request.requestUrl!!.encodedPath)
    }

    @Test
    fun getNotFound() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(404, api.get(99).code())
        val request = server.takeRequest()
        assertEquals("/sonarr/api/v3/indexer/99", request.requestUrl!!.encodedPath)
    }

    @Test
    fun deleteHappyPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        assertEquals(Unit, api.delete(id = 1))
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/sonarr/api/v3/indexer/1", request.requestUrl!!.encodedPath)
    }

    @Test
    fun updateBulkHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/indexer/updateBulk.json"))
        )
        val updated = api.updateBulk(
            body = IndexerBulkResource(ids = listOf(1), enableRss = true, priority = 25)
        )
        assertEquals(1, updated.id)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/sonarr/api/v3/indexer/bulk", request.requestUrl!!.encodedPath)
        assertTrue(request.body.size > 0)
    }

    @Test
    fun deleteBulkHappyPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        assertEquals(Unit, api.deleteBulk(body = IndexerBulkResource(ids = listOf(1))))
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/sonarr/api/v3/indexer/bulk", request.requestUrl!!.encodedPath)
        assertTrue(request.body.size > 0)
    }

    @Test
    fun schemaHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/indexer/schema.json"))
        )
        val expected = ArrJson.decodeFromString<List<IndexerResource>>(
            fixture("fixtures/sonarr/indexer/schema.json")
        )
        val schemas = api.schema()
        assertEquals(expected, schemas)
        assertEquals(1, schemas.single().presets!!.size)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/indexer/schema", request.requestUrl!!.encodedPath)
    }

    @Test
    fun testHappyPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        assertEquals(
            Unit, api.test(forceTest = false, body = IndexerResource(name = "NZBgeek"))
        )
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/sonarr/api/v3/indexer/test", request.requestUrl!!.encodedPath)
        assertEquals("false", request.requestUrl!!.queryParameter("forceTest"))
        assertTrue(request.body.size > 0)
    }

    @Test
    fun testAllHappyPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        assertEquals(Unit, api.testAll())
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/sonarr/api/v3/indexer/testall", request.requestUrl!!.encodedPath)
    }

    @Test
    fun actionHappyPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        assertEquals(
            Unit, api.action(name = "getUrls", body = IndexerResource(name = "NZBgeek"))
        )
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/sonarr/api/v3/indexer/action/getUrls", request.requestUrl!!.encodedPath)
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
