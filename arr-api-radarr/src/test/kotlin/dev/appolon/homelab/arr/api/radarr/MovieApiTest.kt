package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.MovieStatusType
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

class MovieApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: dev.appolon.homelab.arr.api.radarr.api.MovieApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = RadarrApis.movie(TestRadarr.retrofit(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun listParsesMoviesAndSendsFilters() = runTest {
        server.enqueue(MockResponse().setBody(TestRadarr.fixture("movie", "movie-list.json")))

        val movies = api.list(tmdbId = 19995, excludeLocalCovers = true, languageId = 1)

        val request = server.takeRequest()
        val expected = "/api/v3/movie?tmdbId=19995&excludeLocalCovers=true&languageId=1"
        assertEquals(expected, request.path)
        assertEquals(2, movies.size)
        assertEquals("Example Movie", movies[0].title)
        assertEquals(MovieStatusType.RELEASED, movies[0].status)
        assertEquals(MovieStatusType.IN_CINEMAS, movies[1].status)
        assertEquals(734003200L, movies[0].sizeOnDisk)
        assertEquals(setOf(1, 2), movies[0].tags)
    }

    @Test
    fun getReturnsMovie() = runTest {
        server.enqueue(MockResponse().setBody(TestRadarr.fixture("movie", "movie.json")))

        val response = api.get(1)

        assertTrue(response.isSuccessful)
        assertEquals(1, response.body()!!.id)
        assertEquals("/api/v3/movie/1", server.takeRequest().path)
    }

    @Test
    fun getSurfacesNotFound() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val response = api.get(999)

        assertEquals(404, response.code())
    }

    @Test
    fun createPostsBodyAndReadsCreated() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(TestRadarr.fixture("movie", "movie.json")),
        )

        val response = api.create(MovieResource(title = "Example Movie", tmdbId = 19995))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v3/movie", request.path)
        assertTrue(request.body.readUtf8().contains("\"title\":\"Example Movie\""))
        assertEquals(201, response.code())
        assertEquals(19995, response.body()!!.tmdbId)
    }

    @Test
    fun updatePutsMovieWithMoveFlag() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(202).setBody(TestRadarr.fixture("movie", "movie.json")),
        )

        val response = api.update(
            "1",
            moveFiles = true,
            movie = MovieResource(id = 1, monitored = false),
        )

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v3/movie/1?moveFiles=true", request.path)
        assertEquals(202, response.code())
    }

    @Test
    fun deleteSendsFlags() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        api.delete(3, deleteFiles = true, addImportExclusion = true)

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v3/movie/3?deleteFiles=true&addImportExclusion=true", request.path)
    }

    @Test
    fun listThrowsHttpExceptionOnServerError() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        val error = runCatching { api.list() }.exceptionOrNull()

        assertTrue(error is HttpException)
        assertEquals(500, (error as HttpException).code())
    }
}
