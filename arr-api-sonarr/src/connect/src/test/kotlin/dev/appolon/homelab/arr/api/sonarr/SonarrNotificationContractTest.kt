package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.NotificationResource
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
class SonarrNotificationContractTest {
    private val server = MockWebServer()
    private lateinit var api: NotificationApi

    @Before
    fun setUp() {
        server.start()
        api = SonarrConnectApis.notification(
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
            MockResponse().setBody(fixture("fixtures/sonarr/notification/list.json"))
        )
        val expected = ArrJson.decodeFromString<List<NotificationResource>>(
            fixture("fixtures/sonarr/notification/list.json")
        )
        val result = api.list()
        assertEquals(expected, result)
        assertEquals(2, result.size)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/notification", request.requestUrl!!.encodedPath)
    }

    @Test
    fun createHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/notification/create.json"))
        )
        val expected = ArrJson.decodeFromString<NotificationResource>(
            fixture("fixtures/sonarr/notification/create.json")
        )
        assertEquals(
            expected, api.create(
                forceSave = false, body = NotificationResource(name = "Webhook")
            )
        )
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/sonarr/api/v3/notification", request.requestUrl!!.encodedPath)
        assertEquals("false", request.requestUrl!!.queryParameter("forceSave"))
        assertTrue(request.body.size > 0)
    }

    @Test
    fun updateHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/notification/update.json"))
        )
        val expected = ArrJson.decodeFromString<NotificationResource>(
            fixture("fixtures/sonarr/notification/update.json")
        )
        assertEquals(
            expected, api.update(
                id = 1, forceSave = false, body = NotificationResource(id = 1, name = "Webhook")
            )
        )
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/sonarr/api/v3/notification/1", request.requestUrl!!.encodedPath)
        assertEquals("false", request.requestUrl!!.queryParameter("forceSave"))
    }

    @Test
    fun getHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/notification/get.json"))
        )
        val expected = ArrJson.decodeFromString<NotificationResource>(
            fixture("fixtures/sonarr/notification/get.json")
        )
        val response = api.get(1)
        assertEquals(200, response.code())
        assertEquals(expected, response.body())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/notification/1", request.requestUrl!!.encodedPath)
    }

    @Test
    fun getNotFound() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(404, api.get(99).code())
        val request = server.takeRequest()
        assertEquals("/sonarr/api/v3/notification/99", request.requestUrl!!.encodedPath)
    }

    @Test
    fun deleteHappyPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        assertEquals(Unit, api.delete(id = 1))
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/sonarr/api/v3/notification/1", request.requestUrl!!.encodedPath)
    }

    @Test
    fun schemaHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/notification/schema.json"))
        )
        val expected = ArrJson.decodeFromString<List<NotificationResource>>(
            fixture("fixtures/sonarr/notification/schema.json")
        )
        val schemas = api.schema()
        assertEquals(expected, schemas)
        assertEquals(1, schemas.single().presets!!.size)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/notification/schema", request.requestUrl!!.encodedPath)
    }

    @Test
    fun testHappyPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        assertEquals(
            Unit, api.test(forceTest = false, body = NotificationResource(name = "Webhook"))
        )
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/sonarr/api/v3/notification/test", request.requestUrl!!.encodedPath)
        assertEquals("false", request.requestUrl!!.queryParameter("forceTest"))
        assertTrue(request.body.size > 0)
    }

    @Test
    fun testAllHappyPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        assertEquals(Unit, api.testAll())
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/sonarr/api/v3/notification/testall", request.requestUrl!!.encodedPath)
    }

    @Test
    fun actionHappyPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        assertEquals(
            Unit, api.action(name = "test", body = NotificationResource(name = "Webhook"))
        )
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/sonarr/api/v3/notification/action/test", request.requestUrl!!.encodedPath)
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
