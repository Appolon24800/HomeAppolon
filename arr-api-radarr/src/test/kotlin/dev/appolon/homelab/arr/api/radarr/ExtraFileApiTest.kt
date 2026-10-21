package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.ExtraFileType
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

class ExtraFileApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: dev.appolon.homelab.arr.api.radarr.api.ExtraFileApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = RadarrApis.extraFile(TestRadarr.retrofit(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun listSendsMovieFilterAndParsesFiles() = runTest {
        val body = TestRadarr.fixture("extrafile", "extrafile-list.json")
        server.enqueue(MockResponse().setBody(body))

        val files = api.list(movieId = 1)

        assertEquals("/api/v3/extrafile?movieId=1", server.takeRequest().path)
        assertEquals(1, files.size)
        assertEquals(ExtraFileType.SUBTITLE, files[0].type)
        assertEquals(listOf("eng"), files[0].languageTags)
    }

    @Test
    fun listThrowsHttpExceptionOnServerError() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val error = runCatching { api.list() }.exceptionOrNull()

        assertTrue(error is HttpException)
        assertEquals(500, (error as HttpException).code())
    }
}
