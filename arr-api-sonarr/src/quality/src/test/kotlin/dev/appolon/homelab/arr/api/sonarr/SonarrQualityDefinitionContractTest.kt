package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.QualityDefinitionLimitsResource
import dev.appolon.homelab.arr.api.sonarr.model.QualityDefinitionResource
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
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

class SonarrQualityDefinitionContractTest {
    private val server = MockWebServer()
    private lateinit var api: QualityDefinitionApi

    @Before
    fun setUp() {
        server.start()
        api = SonarrCalendarApis.qualityDefinition(
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
            MockResponse().setBody(fixture("fixtures/sonarr/qualitydefinition/list.json"))
        )
        val expected = ArrJson.decodeFromString<List<QualityDefinitionResource>>(
            fixture("fixtures/sonarr/qualitydefinition/list.json")
        )
        assertEquals(expected, api.list())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/qualitydefinition", request.requestUrl!!.encodedPath)
    }

    @Test
    fun updateManyHappyPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val body = ArrJson.decodeFromString<List<QualityDefinitionResource>>(
            fixture("fixtures/sonarr/qualitydefinition/list.json")
        )
        assertEquals(Unit, api.updateMany(body = body))
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals(
            "/sonarr/api/v3/qualitydefinition/update",
            request.requestUrl!!.encodedPath
        )
        assertTrue(request.body.size > 0)
    }

    @Test
    fun limitsHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/qualitydefinition/limits.json"))
        )
        val expected = ArrJson.decodeFromString<QualityDefinitionLimitsResource>(
            fixture("fixtures/sonarr/qualitydefinition/limits.json")
        )
        assertEquals(expected, api.limits())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals(
            "/sonarr/api/v3/qualitydefinition/limits",
            request.requestUrl!!.encodedPath
        )
    }

    @Test
    fun updateHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/qualitydefinition/update.json"))
        )
        val body = ArrJson.decodeFromString<QualityDefinitionResource>(
            fixture("fixtures/sonarr/qualitydefinition/get.json")
        )
        val expected = ArrJson.decodeFromString<QualityDefinitionResource>(
            fixture("fixtures/sonarr/qualitydefinition/update.json")
        )
        assertEquals(expected, api.update(id = "2", body = body))
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals(
            "/sonarr/api/v3/qualitydefinition/2",
            request.requestUrl!!.encodedPath
        )
        assertTrue(request.body.size > 0)
    }

    @Test
    fun getHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/qualitydefinition/get.json"))
        )
        val expected = ArrJson.decodeFromString<QualityDefinitionResource>(
            fixture("fixtures/sonarr/qualitydefinition/get.json")
        )
        assertEquals(expected, api.get(id = 1).body())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals(
            "/sonarr/api/v3/qualitydefinition/1",
            request.requestUrl!!.encodedPath
        )
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
