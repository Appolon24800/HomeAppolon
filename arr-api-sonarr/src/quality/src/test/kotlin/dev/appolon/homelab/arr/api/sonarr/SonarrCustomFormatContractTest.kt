package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.CustomFormatBulkResource
import dev.appolon.homelab.arr.api.sonarr.model.CustomFormatResource
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class SonarrCustomFormatContractTest {
    private val server = MockWebServer()
    private lateinit var api: CustomFormatApi

    @Before
    fun setUp() {
        server.start()
        api = SonarrCalendarApis.customFormat(
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
            MockResponse().setBody(fixture("fixtures/sonarr/customformat/list.json"))
        )
        val expected = ArrJson.decodeFromString<List<CustomFormatResource>>(
            fixture("fixtures/sonarr/customformat/list.json")
        )
        assertEquals(expected, api.list())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/customformat", request.requestUrl!!.encodedPath)
    }

    @Test
    fun createHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/customformat/create.json"))
        )
        val body = CustomFormatResource(name = "AMZN")
        val expected = ArrJson.decodeFromString<CustomFormatResource>(
            fixture("fixtures/sonarr/customformat/create.json")
        )
        assertEquals(expected, api.create(body = body))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/sonarr/api/v3/customformat", request.requestUrl!!.encodedPath)
        assertTrue(request.body.size > 0)
    }

    @Test
    fun updateBulkHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/customformat/get.json"))
        )
        val body = CustomFormatBulkResource(
            ids = listOf(1, 2), includeCustomFormatWhenRenaming = true
        )
        val expected = ArrJson.decodeFromString<CustomFormatResource>(
            fixture("fixtures/sonarr/customformat/get.json")
        )
        assertEquals(expected, api.updateBulk(body = body))
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/sonarr/api/v3/customformat/bulk", request.requestUrl!!.encodedPath)
        assertTrue(request.body.size > 0)
    }

    @Test
    fun deleteBulkHappyPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val body = CustomFormatBulkResource(ids = listOf(1, 2))
        assertEquals(Unit, api.deleteBulk(body = body))
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/sonarr/api/v3/customformat/bulk", request.requestUrl!!.encodedPath)
        assertTrue(request.body.size > 0)
    }

    @Test
    fun schemaHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/customformat/schema.json"))
        )
        val expected = ArrJson.decodeFromString<List<JsonObject>>(
            fixture("fixtures/sonarr/customformat/schema.json")
        )
        assertEquals(expected, api.schema())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/customformat/schema", request.requestUrl!!.encodedPath)
    }

    @Test
    fun updateHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/customformat/get.json"))
        )
        val body = ArrJson.decodeFromString<CustomFormatResource>(
            fixture("fixtures/sonarr/customformat/get.json")
        )
        assertEquals(body, api.update(id = "1", body = body))
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/sonarr/api/v3/customformat/1", request.requestUrl!!.encodedPath)
        assertTrue(request.body.size > 0)
    }

    @Test
    fun deleteHappyPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        assertEquals(Unit, api.delete(id = 1))
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/sonarr/api/v3/customformat/1", request.requestUrl!!.encodedPath)
    }

    @Test
    fun getHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/customformat/get.json"))
        )
        val expected = ArrJson.decodeFromString<CustomFormatResource>(
            fixture("fixtures/sonarr/customformat/get.json")
        )
        assertEquals(expected, api.get(id = 1).body())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/customformat/1", request.requestUrl!!.encodedPath)
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
            api.list()
            fail("Expected HTTP failure")
        } catch (error: HttpException) {
            assertEquals(503, error.code())
        }
    }
}
