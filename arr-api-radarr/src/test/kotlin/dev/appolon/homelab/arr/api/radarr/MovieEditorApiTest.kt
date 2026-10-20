package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.ApplyTags
import dev.appolon.homelab.arr.api.radarr.model.MovieEditorResource
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MovieEditorApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: dev.appolon.homelab.arr.api.radarr.api.MovieEditorApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = RadarrApis.movieEditor(TestRadarr.retrofit(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun updatePutsEditorResourceAndParsesResult() = runTest {
        server.enqueue(MockResponse().setBody(TestRadarr.fixture("movie", "movie-list.json")))

        val response = api.update(
            MovieEditorResource(
                movieIds = listOf(1, 2),
                monitored = true,
                applyTags = ApplyTags.ADD,
                tags = listOf(3),
            ),
        )

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v3/movie/editor", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"movieIds\":[1,2]"))
        assertTrue(body.contains("\"applyTags\":\"add\""))
        assertTrue(response.isSuccessful)
        assertEquals(2, response.body()!!.size)
    }

    @Test
    fun updateSurfacesBadRequest() = runTest {
        server.enqueue(MockResponse().setResponseCode(400))

        val response = api.update(MovieEditorResource(movieIds = listOf(1)))

        assertEquals(400, response.code())
    }

    @Test
    fun deletePostsBodyToEditorPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        api.delete(MovieEditorResource(movieIds = listOf(1), deleteFiles = true))

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v3/movie/editor", request.path)
        assertTrue(request.body.readUtf8().contains("\"deleteFiles\":true"))
    }
}
