package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.NamingConfigResource
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
class SonarrNamingConfigContractTest {
    private val server = MockWebServer()
    private lateinit var api: NamingConfigApi

    @Before
    fun setUp() {
        server.start()
        api = SonarrSystemApis.namingConfig(
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
    fun getHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/namingconfig/get.json"))
        )
        val expected = ArrJson.decodeFromString<NamingConfigResource>(
            fixture("fixtures/sonarr/namingconfig/get.json")
        )
        assertEquals(expected, api.get())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/config/naming", request.requestUrl!!.encodedPath)
    }

    @Test
    fun updateHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/namingconfig/get.json"))
        )
        val expected = ArrJson.decodeFromString<NamingConfigResource>(
            fixture("fixtures/sonarr/namingconfig/get.json")
        )
        assertEquals(expected, api.update(id = "1", body = expected))
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/sonarr/api/v3/config/naming/1", request.requestUrl!!.encodedPath)
        assertTrue(request.body.size > 0)
    }

    @Test
    fun getByIdHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/namingconfig/get.json"))
        )
        val expected = ArrJson.decodeFromString<NamingConfigResource>(
            fixture("fixtures/sonarr/namingconfig/get.json")
        )
        val response = api.get(1)
        assertEquals(200, response.code())
        assertEquals(expected, response.body())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/config/naming/1", request.requestUrl!!.encodedPath)
    }

    @Test
    fun examplesHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/namingconfig/examples.json"))
        )
        val expected: JsonObject = ArrJson.decodeFromString<JsonObject>(
            fixture("fixtures/sonarr/namingconfig/examples.json")
        )
        assertEquals(
            expected, api.examples(
                renameEpisodes = true,
                standardEpisodeFormat = "Series Title - S{season:00}E{episode:00}"
            )
        )
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/config/naming/examples", request.requestUrl!!.encodedPath)
        assertEquals("true", request.requestUrl!!.queryParameter("renameEpisodes"))
        assertEquals(
            "Series Title - S{season:00}E{episode:00}",
            request.requestUrl!!.queryParameter("standardEpisodeFormat")
        )
    }

    @Test
    fun httpError() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
        try {
            api.get()
            fail("Expected HTTP failure")
        } catch (error: HttpException) {
            assertEquals(500, error.code())
        }
    }
}
