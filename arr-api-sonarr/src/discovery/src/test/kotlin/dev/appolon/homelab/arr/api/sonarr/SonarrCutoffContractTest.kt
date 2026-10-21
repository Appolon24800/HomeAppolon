package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResourcePagingResource
import dev.appolon.homelab.arr.api.sonarr.model.SortDirection
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
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

class SonarrCutoffContractTest {
    private val server = MockWebServer()
    private lateinit var api: CutoffApi

    @Before
    fun setUp() {
        server.start()
        api = SonarrDiscoveryApis.cutoff(
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
            MockResponse().setBody(fixture("fixtures/sonarr/cutoff/list.json"))
        )
        val expected = ArrJson.decodeFromString<EpisodeResourcePagingResource>(
            fixture("fixtures/sonarr/cutoff/list.json")
        )
        assertEquals(
            expected, api.list(
                page = 1, pageSize = 10, sortKey = "sample", sortDirection = SortDirection.DEFAULT,
                includeSeries = false, includeEpisodeFile = false, includeImages = false,
                monitored = true
            )
        )
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/wanted/cutoff", request.requestUrl!!.encodedPath)
        assertEquals("1", request.requestUrl!!.queryParameter("page"))
        assertEquals("10", request.requestUrl!!.queryParameter("pageSize"))
        assertEquals("sample", request.requestUrl!!.queryParameter("sortKey"))
        assertEquals("default", request.requestUrl!!.queryParameter("sortDirection"))
        assertEquals("false", request.requestUrl!!.queryParameter("includeSeries"))
        assertEquals("false", request.requestUrl!!.queryParameter("includeEpisodeFile"))
        assertEquals("false", request.requestUrl!!.queryParameter("includeImages"))
        assertEquals("true", request.requestUrl!!.queryParameter("monitored"))
    }

    @Test
    fun getHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/cutoff/get.json"))
        )
        val expected = ArrJson.decodeFromString<EpisodeResource>(
            fixture("fixtures/sonarr/cutoff/get.json")
        )
        val response = api.get(2)
        assertEquals(200, response.code())
        assertEquals(expected, response.body())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/wanted/cutoff/2", request.requestUrl!!.encodedPath)
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
