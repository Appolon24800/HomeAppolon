package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.DatabaseType
import dev.appolon.homelab.arr.api.sonarr.model.RuntimeMode
import dev.appolon.homelab.arr.api.sonarr.model.SystemResource
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
class SonarrSystemContractTest {
    private val server = MockWebServer()
    private lateinit var api: SystemApi

    @Before
    fun setUp() {
        server.start()
        api = SonarrSystemApis.system(
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
    fun statusHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/system/status.json"))
        )
        val expected = ArrJson.decodeFromString<SystemResource>(
            fixture("fixtures/sonarr/system/status.json")
        )
        assertEquals(RuntimeMode.CONSOLE, expected.mode)
        assertEquals(DatabaseType.SQLITE, expected.databaseType)
        assertEquals(expected, api.status())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/system/status", request.requestUrl!!.encodedPath)
    }

    @Test
    fun routesHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/system/routes.json"))
        )
        val expected: JsonObject = ArrJson.decodeFromString<JsonObject>(
            fixture("fixtures/sonarr/system/routes.json")
        )
        assertEquals(expected, api.routes())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/system/routes", request.requestUrl!!.encodedPath)
    }

    @Test
    fun routesDuplicateHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/system/routesduplicate.json"))
        )
        val expected: JsonObject = ArrJson.decodeFromString<JsonObject>(
            fixture("fixtures/sonarr/system/routesduplicate.json")
        )
        assertEquals(expected, api.routesDuplicate())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/system/routes/duplicate", request.requestUrl!!.encodedPath)
    }

    @Test
    fun shutdownHappyPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        api.shutdown()
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/sonarr/api/v3/system/shutdown", request.requestUrl!!.encodedPath)
        assertTrue(request.body.size == 0L)
    }

    @Test
    fun restartHappyPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        api.restart()
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/sonarr/api/v3/system/restart", request.requestUrl!!.encodedPath)
    }

    @Test
    fun httpError() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
        try {
            api.status()
            fail("Expected HTTP failure")
        } catch (error: HttpException) {
            assertEquals(500, error.code())
        }
    }
}
