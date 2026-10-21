package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.MovieFileListResource
import dev.appolon.homelab.arr.api.radarr.model.MovieFileResource
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MovieFileApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: dev.appolon.homelab.arr.api.radarr.api.MovieFileApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = RadarrApis.movieFile(TestRadarr.retrofit(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun listRepeatsQueryPerValue() = runTest {
        val body = TestRadarr.fixture("movie-file", "movie-file-list.json")
        server.enqueue(MockResponse().setBody(body))

        val files = api.list(movieId = listOf(1, 2))

        assertEquals("/api/v3/moviefile?movieId=1&movieId=2", server.takeRequest().path)
        assertEquals(1, files.size)
        val file = files[0]
        assertEquals(25, file.id)
        assertEquals(734003200L, file.size)
        assertEquals("Bluray-1080p", file.quality?.quality?.name)
        assertEquals("HQ", file.customFormats?.first()?.name)
        assertEquals(5.1, file.mediaInfo?.audioChannels!!, 0.0)
    }

    @Test
    fun getReturnsFile() = runTest {
        server.enqueue(MockResponse().setBody(TestRadarr.fixture("movie-file", "movie-file.json")))

        val response = api.get(25)

        assertTrue(response.isSuccessful)
        assertEquals(25, response.body()!!.id)
        assertEquals("/api/v3/moviefile/25", server.takeRequest().path)
    }

    @Test
    fun getSurfacesNotFound() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        assertEquals(404, api.get(999).code())
    }

    @Test
    fun updatePutsFileAndReadsAccepted() = runTest {
        val body = TestRadarr.fixture("movie-file", "movie-file.json")
        server.enqueue(MockResponse().setResponseCode(202).setBody(body))

        val response = api.update("25", MovieFileResource(id = 25, movieId = 1))

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v3/moviefile/25", request.path)
        assertEquals(202, response.code())
    }

    @Test
    fun deleteSurfacesNotFound() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        assertEquals(404, api.delete(999).code())
    }

    @Test
    fun updateBulkPutsArray() = runTest {
        val body = TestRadarr.fixture("movie-file", "movie-file-list.json")
        server.enqueue(MockResponse().setBody(body))

        val files = api.updateBulk(listOf(MovieFileResource(id = 25, movieId = 1)))

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v3/moviefile/bulk", request.path)
        assertTrue(request.body.readUtf8().startsWith("["))
        assertEquals(1, files.size)
    }

    @Test
    fun deleteBulkPostsListResource() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        api.deleteBulk(MovieFileListResource(movieFileIds = listOf(25, 26)))

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v3/moviefile/bulk", request.path)
        assertTrue(request.body.readUtf8().contains("\"movieFileIds\":[25,26]"))
    }

    @Suppress("DEPRECATION")
    @Test
    fun updateEditorPutsLegacyPath() = runTest {
        val body = TestRadarr.fixture("movie-file", "movie-file-list.json")
        server.enqueue(MockResponse().setBody(body))

        val files = api.updateEditor(MovieFileListResource(movieFileIds = listOf(25)))

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v3/moviefile/editor", request.path)
        assertEquals(1, files.size)
    }
}
