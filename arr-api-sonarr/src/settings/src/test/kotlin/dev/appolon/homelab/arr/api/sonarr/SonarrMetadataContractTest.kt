package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.MetadataResource
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Suppress("DEPRECATION")
class SonarrMetadataContractTest {
    private val server = MockWebServer()
    private lateinit var api: MetadataApi

    @Before
    fun setUp() {
        server.start()
        api = SonarrSystemApis.metadata(
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
            MockResponse().setBody(fixture("fixtures/sonarr/metadata/list.json"))
        )
        val expected = ArrJson.decodeFromString<List<MetadataResource>>(
            fixture("fixtures/sonarr/metadata/list.json")
        )
        assertEquals(expected, api.list())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/metadata", request.requestUrl!!.encodedPath)
    }

    @Test
    fun createHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/metadata/get.json"))
        )
        val expected = ArrJson.decodeFromString<MetadataResource>(
            fixture("fixtures/sonarr/metadata/get.json")
        )
        assertEquals(expected, api.create(body = MetadataResource(name = "Kodi")))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/sonarr/api/v3/metadata", request.requestUrl!!.encodedPath)
        assertNull(request.requestUrl!!.queryParameter("forceSave"))
    }

    @Test
    fun updateHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/metadata/get.json"))
        )
        val expected = ArrJson.decodeFromString<MetadataResource>(
            fixture("fixtures/sonarr/metadata/get.json")
        )
        assertEquals(
            expected, api.update(
                id = 1, forceSave = true, body = expected
            )
        )
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/sonarr/api/v3/metadata/1", request.requestUrl!!.encodedPath)
        assertEquals("true", request.requestUrl!!.queryParameter("forceSave"))
    }

    @Test
    fun deleteHappyPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        api.delete(1)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/sonarr/api/v3/metadata/1", request.requestUrl!!.encodedPath)
    }

    @Test
    fun getHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/metadata/get.json"))
        )
        val expected = ArrJson.decodeFromString<MetadataResource>(
            fixture("fixtures/sonarr/metadata/get.json")
        )
        val response = api.get(1)
        assertEquals(200, response.code())
        assertEquals(expected, response.body())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/metadata/1", request.requestUrl!!.encodedPath)
    }

    @Test
    fun getNotFound() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val response = api.get(99)
        assertEquals(404, response.code())
    }

    @Test
    fun schemaHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/metadata/schema.json"))
        )
        val expected = ArrJson.decodeFromString<List<MetadataResource>>(
            fixture("fixtures/sonarr/metadata/schema.json")
        )
        assertEquals(expected, api.schema())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/metadata/schema", request.requestUrl!!.encodedPath)
    }

    @Test
    fun testHappyPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        api.test(forceTest = true, body = MetadataResource(name = "Kodi"))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/sonarr/api/v3/metadata/test", request.requestUrl!!.encodedPath)
        assertEquals("true", request.requestUrl!!.queryParameter("forceTest"))
        assertTrue(request.body.size > 0)
    }

    @Test
    fun testAllHappyPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        api.testAll()
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/sonarr/api/v3/metadata/testall", request.requestUrl!!.encodedPath)
    }

    @Test
    fun actionHappyPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        api.action(name = "discover", body = MetadataResource(name = "Kodi"))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/sonarr/api/v3/metadata/action/discover", request.requestUrl!!.encodedPath)
    }

    @Test
    fun httpError() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
        try {
            api.list()
            fail("Expected HTTP failure")
        } catch (error: HttpException) {
            assertEquals(500, error.code())
        }
    }
}
