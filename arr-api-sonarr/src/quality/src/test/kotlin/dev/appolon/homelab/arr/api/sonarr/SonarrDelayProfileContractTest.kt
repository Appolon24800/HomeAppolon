package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.DelayProfileResource
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

class SonarrDelayProfileContractTest {
    private val server = MockWebServer()
    private lateinit var api: DelayProfileApi

    @Before
    fun setUp() {
        server.start()
        api = SonarrCalendarApis.delayProfile(
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
            MockResponse().setBody(fixture("fixtures/sonarr/delayprofile/list.json"))
        )
        val expected = ArrJson.decodeFromString<List<DelayProfileResource>>(
            fixture("fixtures/sonarr/delayprofile/list.json")
        )
        assertEquals(expected, api.list())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/delayprofile", request.requestUrl!!.encodedPath)
    }

    @Test
    fun createHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/delayprofile/create.json"))
        )
        val body = DelayProfileResource(usenetDelay = 90)
        val expected = ArrJson.decodeFromString<DelayProfileResource>(
            fixture("fixtures/sonarr/delayprofile/create.json")
        )
        assertEquals(expected, api.create(body = body))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/sonarr/api/v3/delayprofile", request.requestUrl!!.encodedPath)
        assertTrue(request.body.size > 0)
    }

    @Test
    fun reorderHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/delayprofile/reorder.json"))
        )
        val expected = ArrJson.decodeFromString<List<DelayProfileResource>>(
            fixture("fixtures/sonarr/delayprofile/reorder.json")
        )
        assertEquals(expected, api.reorder(id = 2, after = null))
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/sonarr/api/v3/delayprofile/reorder/2", request.requestUrl!!.encodedPath)
    }

    @Test
    fun updateHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/delayprofile/get.json"))
        )
        val body = ArrJson.decodeFromString<DelayProfileResource>(
            fixture("fixtures/sonarr/delayprofile/get.json")
        )
        assertEquals(body, api.update(id = "1", body = body))
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/sonarr/api/v3/delayprofile/1", request.requestUrl!!.encodedPath)
        assertTrue(request.body.size > 0)
    }

    @Test
    fun deleteHappyPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        assertEquals(Unit, api.delete(id = 1))
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/sonarr/api/v3/delayprofile/1", request.requestUrl!!.encodedPath)
    }

    @Test
    fun getHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/delayprofile/get.json"))
        )
        val expected = ArrJson.decodeFromString<DelayProfileResource>(
            fixture("fixtures/sonarr/delayprofile/get.json")
        )
        assertEquals(expected, api.get(id = 1).body())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/delayprofile/1", request.requestUrl!!.encodedPath)
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
