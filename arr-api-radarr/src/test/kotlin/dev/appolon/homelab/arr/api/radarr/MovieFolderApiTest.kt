package dev.appolon.homelab.arr.api.radarr

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

class MovieFolderApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: dev.appolon.homelab.arr.api.radarr.api.MovieFolderApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = RadarrApis.movieFolder(TestRadarr.retrofit(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun getReturnsFolder() = runTest {
        server.enqueue(MockResponse().setBody(TestRadarr.fixture("movie-folder", "folder.json")))

        val folder = api.get(1)

        assertEquals("Example Movie (2009)", folder.folder)
        assertEquals("/api/v3/movie/1/folder", server.takeRequest().path)
    }

    @Test
    fun getThrowsHttpExceptionOnServerError() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val error = runCatching { api.get(1) }.exceptionOrNull()

        assertTrue(error is HttpException)
        assertEquals(500, (error as HttpException).code())
    }
}
