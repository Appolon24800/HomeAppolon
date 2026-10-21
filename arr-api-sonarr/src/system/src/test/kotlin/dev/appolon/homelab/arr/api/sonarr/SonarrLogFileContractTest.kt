package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.LogFileResource
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Suppress("DEPRECATION")
class SonarrLogFileContractTest {
    private val server = MockWebServer()
    private lateinit var api: LogFileApi

    @Before
    fun setUp() {
        server.start()
        api = SonarrSystemApis.logFile(
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
            MockResponse().setBody(fixture("fixtures/sonarr/logfile/list.json"))
        )
        val expected = ArrJson.decodeFromString<List<LogFileResource>>(
            fixture("fixtures/sonarr/logfile/list.json")
        )
        assertEquals("sonarr.trace.txt", expected[0].filename)
        assertEquals(expected, api.list())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/log/file", request.requestUrl!!.encodedPath)
    }

    @Test
    fun getHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/logfile/get.json"))
        )
        val expected: JsonObject = ArrJson.decodeFromString<JsonObject>(
            fixture("fixtures/sonarr/logfile/get.json")
        )
        assertEquals(expected, api.get("sonarr.trace.txt"))
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals(
            "/sonarr/api/v3/log/file/sonarr.trace.txt", request.requestUrl!!.encodedPath
        )
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
