package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.ApplyTags
import dev.appolon.homelab.arr.api.sonarr.model.ImportListBulkResource
import dev.appolon.homelab.arr.api.sonarr.model.ImportListResource
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

class SonarrImportListContractTest {
    private val server = MockWebServer()
    private lateinit var api: ImportListApi

    @Before
    fun setUp() {
        server.start()
        api = SonarrDiscoveryApis.importList(
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
            MockResponse().setBody(fixture("fixtures/sonarr/importlist/list.json"))
        )
        val expected = ArrJson.decodeFromString<List<ImportListResource>>(
            fixture("fixtures/sonarr/importlist/list.json")
        )
        assertEquals(expected, api.list())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/importlist", request.requestUrl!!.encodedPath)
    }

    @Test
    fun createHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/importlist/create.json"))
        )
        val expected = ArrJson.decodeFromString<ImportListResource>(
            fixture("fixtures/sonarr/importlist/create.json")
        )
        assertEquals(
            expected, api.create(
                forceSave = false,
                body = ImportListResource(name = "string", implementation = "string")
            )
        )
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/sonarr/api/v3/importlist", request.requestUrl!!.encodedPath)
        assertEquals("false", request.requestUrl!!.queryParameter("forceSave"))
        assertTrue(request.body.size > 0)
    }

    @Test
    fun updateHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/importlist/update.json"))
        )
        val expected = ArrJson.decodeFromString<ImportListResource>(
            fixture("fixtures/sonarr/importlist/update.json")
        )
        assertEquals(
            expected, api.update(
                id = 1, forceSave = false, body = ImportListResource(id = 1, name = "string")
            )
        )
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/sonarr/api/v3/importlist/1", request.requestUrl!!.encodedPath)
        assertEquals("false", request.requestUrl!!.queryParameter("forceSave"))
    }

    @Test
    fun getHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/importlist/get.json"))
        )
        val expected = ArrJson.decodeFromString<ImportListResource>(
            fixture("fixtures/sonarr/importlist/get.json")
        )
        val response = api.get(1)
        assertEquals(200, response.code())
        assertEquals(expected, response.body())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/importlist/1", request.requestUrl!!.encodedPath)
    }

    @Test
    fun deleteHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/importlist/delete.json"))
        )
        assertEquals(Unit, api.delete(id = 1))
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/sonarr/api/v3/importlist/1", request.requestUrl!!.encodedPath)
    }

    @Test
    fun updateBulkHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/importlist/updateBulk.json"))
        )
        val updated = api.updateBulk(
            body = ImportListBulkResource(
                ids = listOf(1), enableAutomaticAdd = true,
                tags = listOf(1), applyTags = ApplyTags.ADD
            )
        )
        assertEquals(1, updated.id)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/sonarr/api/v3/importlist/bulk", request.requestUrl!!.encodedPath)
        assertTrue(request.body.size > 0)
    }

    @Test
    fun deleteBulkHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/importlist/deleteBulk.json"))
        )
        assertEquals(Unit, api.deleteBulk(body = ImportListBulkResource(ids = listOf(1))))
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/sonarr/api/v3/importlist/bulk", request.requestUrl!!.encodedPath)
        assertTrue(request.body.size > 0)
    }

    @Test
    fun schemaHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/importlist/schema.json"))
        )
        val expected = ArrJson.decodeFromString<List<ImportListResource>>(
            fixture("fixtures/sonarr/importlist/schema.json")
        )
        val schemas = api.schema()
        assertEquals(expected, schemas)
        assertEquals(1, schemas.single().presets!!.size)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/importlist/schema", request.requestUrl!!.encodedPath)
    }

    @Test
    fun testHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/importlist/test.json"))
        )
        assertEquals(
            Unit, api.test(forceTest = false, body = ImportListResource(name = "string"))
        )
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/sonarr/api/v3/importlist/test", request.requestUrl!!.encodedPath)
        assertEquals("false", request.requestUrl!!.queryParameter("forceTest"))
        assertTrue(request.body.size > 0)
    }

    @Test
    fun testAllHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/importlist/testall.json"))
        )
        assertEquals(Unit, api.testAll())
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/sonarr/api/v3/importlist/testall", request.requestUrl!!.encodedPath)
    }

    @Test
    fun actionHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/importlist/action.json"))
        )
        assertEquals(
            Unit, api.action(name = "discover", body = ImportListResource(name = "string"))
        )
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals(
            "/sonarr/api/v3/importlist/action/discover", request.requestUrl!!.encodedPath
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
