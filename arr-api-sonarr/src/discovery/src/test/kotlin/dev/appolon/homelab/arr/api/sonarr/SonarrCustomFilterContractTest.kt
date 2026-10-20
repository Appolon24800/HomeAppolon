package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.CustomFilterResource
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

class SonarrCustomFilterContractTest {
    private val server = MockWebServer()
    private lateinit var api: CustomFilterApi

    @Before
    fun setUp() {
        server.start()
        api = SonarrDiscoveryApis.customFilter(
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
            MockResponse().setBody(fixture("fixtures/sonarr/customfilter/list.json"))
        )
        val expected = ArrJson.decodeFromString<List<CustomFilterResource>>(
            fixture("fixtures/sonarr/customfilter/list.json")
        )
        assertEquals(expected, api.list())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/customfilter", request.requestUrl!!.encodedPath)
    }

    @Test
    fun createHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/customfilter/create.json"))
        )
        val expected = ArrJson.decodeFromString<CustomFilterResource>(
            fixture("fixtures/sonarr/customfilter/create.json")
        )
        assertEquals(
            expected, api.create(
                body = CustomFilterResource(id = 1, type = "seriesIndex", label = "string")
            )
        )
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/sonarr/api/v3/customfilter", request.requestUrl!!.encodedPath)
        assertTrue(request.body.size > 0)
    }

    @Test
    fun updateHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/customfilter/update.json"))
        )
        val expected = ArrJson.decodeFromString<CustomFilterResource>(
            fixture("fixtures/sonarr/customfilter/update.json")
        )
        assertEquals(
            expected, api.update(
                id = "1", body = CustomFilterResource(id = 1, type = "seriesIndex")
            )
        )
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/sonarr/api/v3/customfilter/1", request.requestUrl!!.encodedPath)
        assertTrue(request.body.size > 0)
    }

    @Test
    fun getHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/customfilter/get.json"))
        )
        val expected = ArrJson.decodeFromString<CustomFilterResource>(
            fixture("fixtures/sonarr/customfilter/get.json")
        )
        val response = api.get(1)
        assertEquals(200, response.code())
        assertEquals(expected, response.body())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/customfilter/1", request.requestUrl!!.encodedPath)
    }

    @Test
    fun deleteHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/customfilter/delete.json"))
        )
        assertEquals(Unit, api.delete(id = 1))
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/sonarr/api/v3/customfilter/1", request.requestUrl!!.encodedPath)
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
