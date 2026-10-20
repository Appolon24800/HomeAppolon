package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

class MovieImportApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: dev.appolon.homelab.arr.api.radarr.api.MovieImportApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = RadarrApis.movieImport(TestRadarr.retrofit(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun importPostsArrayOfMovies() = runTest {
        server.enqueue(MockResponse().setBody(TestRadarr.fixture("movie", "movie-list.json")))

        val imported = api.importMovies(
            listOf(MovieResource(title = "Example Movie", tmdbId = 19995, qualityProfileId = 1)),
        )

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v3/movie/import", request.path)
        assertTrue(request.body.readUtf8().startsWith("["))
        assertEquals(2, imported.size)
    }

    @Test
    fun importThrowsHttpExceptionOnBadRequest() = runTest {
        server.enqueue(MockResponse().setResponseCode(400))

        val error = runCatching { api.importMovies(emptyList()) }.exceptionOrNull()

        assertTrue(error is HttpException)
        assertEquals(400, (error as HttpException).code())
    }
}
