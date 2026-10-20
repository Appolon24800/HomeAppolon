package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.LogResourcePagingResource
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

@Suppress("DEPRECATION")
class SonarrLogContractTest {
    private val server = MockWebServer()
    private lateinit var api: LogApi

    @Before
    fun setUp() {
        server.start()
        api = SonarrSystemApis.log(
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
            MockResponse().setBody(fixture("fixtures/sonarr/log/list.json"))
        )
        val expected = ArrJson.decodeFromString<LogResourcePagingResource>(
            fixture("fixtures/sonarr/log/list.json")
        )
        assertEquals(SortDirection.DESCENDING, expected.sortDirection)
        assertEquals(2, expected.records!!.size)
        assertEquals(
            expected, api.list(
                page = 1, pageSize = 10, sortKey = "time",
                sortDirection = SortDirection.DESCENDING, level = "info"
            )
        )
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/log", request.requestUrl!!.encodedPath)
        assertEquals("1", request.requestUrl!!.queryParameter("page"))
        assertEquals("10", request.requestUrl!!.queryParameter("pageSize"))
        assertEquals("time", request.requestUrl!!.queryParameter("sortKey"))
        assertEquals("descending", request.requestUrl!!.queryParameter("sortDirection"))
        assertEquals("info", request.requestUrl!!.queryParameter("level"))
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
