package dev.appolon.homelab.arr.api.sonarr

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import dev.appolon.homelab.arr.net.ArrJson
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

class SonarrFileSystemContractTest {
    private val server = MockWebServer()
    private lateinit var api: FileSystemApi

    @Before
    fun setUp() {
        server.start()
        api = SonarrSystemApis.fileSystem(
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
            MockResponse().setBody(fixture("fixtures/sonarr/filesystem/list.json"))
        )
        val expected: JsonObject = ArrJson.decodeFromString<JsonObject>(
            fixture("fixtures/sonarr/filesystem/list.json")
        )
        assertEquals(expected, api.list(path = "/tv", includeFiles = true))
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/filesystem", request.requestUrl!!.encodedPath)
        assertEquals("/tv", request.requestUrl!!.queryParameter("path"))
        assertEquals("true", request.requestUrl!!.queryParameter("includeFiles"))
    }

    @Test
    fun typeHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/filesystem/type.json"))
        )
        val expected: JsonObject = ArrJson.decodeFromString<JsonObject>(
            fixture("fixtures/sonarr/filesystem/type.json")
        )
        assertEquals(expected, api.type(path = "/tv"))
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/filesystem/type", request.requestUrl!!.encodedPath)
    }

    @Test
    fun mediaFilesHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/filesystem/mediafiles.json"))
        )
        val expected: JsonObject = ArrJson.decodeFromString<JsonObject>(
            fixture("fixtures/sonarr/filesystem/mediafiles.json")
        )
        assertEquals(expected, api.mediaFiles(path = "/tv"))
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/filesystem/mediafiles", request.requestUrl!!.encodedPath)
        assertTrue(request.requestUrl!!.queryParameterNames.contains("path"))
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
