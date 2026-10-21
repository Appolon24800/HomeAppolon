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

class MovieLookupApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: dev.appolon.homelab.arr.api.radarr.api.MovieLookupApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = RadarrApis.movieLookup(TestRadarr.retrofit(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun lookupSendsTermAndParsesResults() = runTest {
        val body = TestRadarr.fixture("movie-lookup", "lookup-list.json")
        server.enqueue(MockResponse().setBody(body))

        val results = api.lookup("example")

        assertEquals("/api/v3/movie/lookup?term=example", server.takeRequest().path)
        assertEquals(1, results.size)
        assertEquals(19995, results[0].tmdbId)
    }

    @Test
    fun lookupByTmdbIdSendsQuery() = runTest {
        val body = TestRadarr.fixture("movie-lookup", "lookup-single.json")
        server.enqueue(MockResponse().setBody(body))

        val movie = api.lookupByTmdbId(19995)

        assertEquals("/api/v3/movie/lookup/tmdb?tmdbId=19995", server.takeRequest().path)
        assertEquals(19995, movie.tmdbId)
    }

    @Test
    fun lookupByImdbIdSendsQuery() = runTest {
        val body = TestRadarr.fixture("movie-lookup", "lookup-single.json")
        server.enqueue(MockResponse().setBody(body))

        val movie = api.lookupByImdbId("tt0000001")

        assertEquals("/api/v3/movie/lookup/imdb?imdbId=tt0000001", server.takeRequest().path)
        assertEquals("tt0000001", movie.imdbId)
    }

    @Test
    fun lookupThrowsHttpExceptionOnAuthFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        val error = runCatching { api.lookup("example") }.exceptionOrNull()

        assertTrue(error is HttpException)
        assertEquals(401, (error as HttpException).code())
    }
}
