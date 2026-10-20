package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.CommandResource
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

class SonarrCommandContractTest {
    private val server = MockWebServer()
    private lateinit var api: CommandApi

    @Before
    fun setUp() {
        server.start()
        api = SonarrQueueApis.command(
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
    fun createHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/command/create.json"))
        )
        val expected = ArrJson.decodeFromString<CommandResource>(
            fixture("fixtures/sonarr/command/create.json")
        )
        val actual = api.create(body = CommandResource())
        assertEquals(expected, actual)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/sonarr/api/v3/command", request.requestUrl!!.encodedPath)

        assertTrue(request.body.size > 0)
    }

    @Test
    fun listHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/command/list.json"))
        )
        val expected = ArrJson.decodeFromString<List<CommandResource>>(
            fixture("fixtures/sonarr/command/list.json")
        )
        val actual = api.list()
        assertEquals(expected, actual)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/command", request.requestUrl!!.encodedPath)


    }

    @Test
    fun deleteHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/command/delete.json"))
        )
        assertEquals(Unit, api.delete(id = 7))
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/sonarr/api/v3/command/7", request.requestUrl!!.encodedPath)


    }

    @Test
    fun getHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/command/get.json"))
        )
        val expected = ArrJson.decodeFromString<CommandResource>(
            fixture("fixtures/sonarr/command/get.json")
        )
        val actual = api.get(id = 7)
        assertEquals(200, actual.code())
        assertEquals(expected, actual.body())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/command/7", request.requestUrl!!.encodedPath)


    }

    @Test
    fun httpError() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        try {
            api.create(
                body = CommandResource()
            )
            fail("Expected HTTP failure")
        } catch (error: HttpException) {
            assertEquals(503, error.code())
        }
    }
}
