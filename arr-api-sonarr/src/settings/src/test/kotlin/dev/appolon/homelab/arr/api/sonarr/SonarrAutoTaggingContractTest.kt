package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.AutoTaggingResource
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
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
class SonarrAutoTaggingContractTest {
    private val server = MockWebServer()
    private lateinit var api: AutoTaggingApi

    @Before
    fun setUp() {
        server.start()
        api = SonarrSystemApis.autoTagging(
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
            MockResponse().setBody(fixture("fixtures/sonarr/autotagging/list.json"))
        )
        val expected = ArrJson.decodeFromString<List<AutoTaggingResource>>(
            fixture("fixtures/sonarr/autotagging/list.json")
        )
        assertEquals(expected, api.list())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/autotagging", request.requestUrl!!.encodedPath)
    }

    @Test
    fun createHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/autotagging/get.json"))
        )
        val expected = ArrJson.decodeFromString<AutoTaggingResource>(
            fixture("fixtures/sonarr/autotagging/get.json")
        )
        assertEquals(expected, api.create(AutoTaggingResource(name = "Anime tagging")))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/sonarr/api/v3/autotagging", request.requestUrl!!.encodedPath)
        assertTrue(request.body.size > 0)
    }

    @Test
    fun updateHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/autotagging/get.json"))
        )
        val expected = ArrJson.decodeFromString<AutoTaggingResource>(
            fixture("fixtures/sonarr/autotagging/get.json")
        )
        assertEquals(expected, api.update(id = "1", body = expected))
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/sonarr/api/v3/autotagging/1", request.requestUrl!!.encodedPath)
    }

    @Test
    fun deleteHappyPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        api.delete(1)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/sonarr/api/v3/autotagging/1", request.requestUrl!!.encodedPath)
    }

    @Test
    fun getHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/autotagging/get.json"))
        )
        val expected = ArrJson.decodeFromString<AutoTaggingResource>(
            fixture("fixtures/sonarr/autotagging/get.json")
        )
        val response = api.get(1)
        assertEquals(200, response.code())
        assertEquals(expected, response.body())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/autotagging/1", request.requestUrl!!.encodedPath)
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
            MockResponse().setBody(fixture("fixtures/sonarr/autotagging/schema.json"))
        )
        val expected: JsonObject = ArrJson.decodeFromString<JsonObject>(
            fixture("fixtures/sonarr/autotagging/schema.json")
        )
        assertEquals(expected, api.schema())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/autotagging/schema", request.requestUrl!!.encodedPath)
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
