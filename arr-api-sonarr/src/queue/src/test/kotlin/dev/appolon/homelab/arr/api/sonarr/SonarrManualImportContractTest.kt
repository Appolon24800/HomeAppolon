package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.ManualImportReprocessResource
import dev.appolon.homelab.arr.api.sonarr.model.ManualImportResource
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

class SonarrManualImportContractTest {
    private val server = MockWebServer()
    private lateinit var api: ManualImportApi

    @Before
    fun setUp() {
        server.start()
        api = SonarrQueueApis.manualImport(
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
            MockResponse().setBody(fixture("fixtures/sonarr/manualimport/list.json"))
        )
        val expected = ArrJson.decodeFromString<List<ManualImportResource>>(
            fixture("fixtures/sonarr/manualimport/list.json")
        )
        val actual = api.list(
            folder = "sample",
            downloadId = "sample",
            seriesId = 7,
            seasonNumber = 7,
            filterExistingFiles = false
        )
        assertEquals(expected, actual)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/manualimport", request.requestUrl!!.encodedPath)
        assertEquals("sample", request.requestUrl!!.queryParameter("folder"))
        assertEquals("sample", request.requestUrl!!.queryParameter("downloadId"))
        assertEquals("7", request.requestUrl!!.queryParameter("seriesId"))
        assertEquals("7", request.requestUrl!!.queryParameter("seasonNumber"))
        assertEquals("false", request.requestUrl!!.queryParameter("filterExistingFiles"))

    }

    @Test
    fun createHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/manualimport/create.json"))
        )
        assertEquals(Unit, api.create(body = listOf(ManualImportReprocessResource())))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/sonarr/api/v3/manualimport", request.requestUrl!!.encodedPath)

        assertTrue(request.body.size > 0)
    }

    @Test
    fun httpError() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        try {
            api.list(
                folder = null,
                downloadId = null,
                seriesId = null,
                seasonNumber = null,
                filterExistingFiles = null
            )
            fail("Expected HTTP failure")
        } catch (error: HttpException) {
            assertEquals(503, error.code())
        }
    }
}
