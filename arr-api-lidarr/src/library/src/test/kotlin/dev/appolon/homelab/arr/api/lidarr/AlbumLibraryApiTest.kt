package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.AlbumsMonitoredResource
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
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
import okhttp3.MediaType.Companion.toMediaType

class AlbumLibraryApiTest {
    private val server = MockWebServer()
    private lateinit var api: AlbumApi

    @Before
    fun setUp() {
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/lidarr/"))
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        api = LidarrLibraryApis.album(retrofit)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun fixture(path: String): String = checkNotNull(
        javaClass.getResourceAsStream("/fixtures/lidarr/$path")
    ).bufferedReader().use { it.readText() }

    @Test
    fun getAlbumsHappyPath() = runTest {
        val fixture = fixture("album/getAlbums.json")
        server.enqueue(MockResponse().setBody(fixture))
        val actual = api.getAlbums(
            artistId = 1,
            albumIds = listOf(1, 2),
            foreignAlbumId = "1",
            includeAllArtistAlbums = true,
        )
        assertEquals(ArrJson.decodeFromString<List<AlbumResource>>(fixture), actual)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals(
            "/lidarr/api/v1/album",
            request.requestUrl!!.encodedPath,
        )
        assertEquals("1", request.requestUrl!!
            .queryParameter("artistId"))
        assertEquals(listOf("1", "2"), request.requestUrl!!
            .queryParameterValues("albumIds"))
        assertEquals("1", request.requestUrl!!
            .queryParameter("foreignAlbumId"))
        assertEquals("true", request.requestUrl!!
            .queryParameter("includeAllArtistAlbums"))
    }

    @Test
    fun addAlbumHappyPath() = runTest {
        val fixture = fixture("album/addAlbum.json")
        server.enqueue(MockResponse().setBody(fixture))
        val actual = api.addAlbum(
            body = AlbumResource(),
        )
        assertEquals(ArrJson.decodeFromString<AlbumResource>(fixture), actual)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals(
            "/lidarr/api/v1/album",
            request.requestUrl!!.encodedPath,
        )
        assertEquals(AlbumResource(), ArrJson.decodeFromString<AlbumResource>(
            request.body.readUtf8()))
    }

    @Test
    fun updateAlbumHappyPath() = runTest {
        val fixture = fixture("album/updateAlbum.json")
        server.enqueue(MockResponse().setBody(fixture))
        val actual = api.updateAlbum(
            id = "1",
            body = AlbumResource(),
        )
        assertEquals(ArrJson.decodeFromString<AlbumResource>(fixture), actual)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals(
            "/lidarr/api/v1/album/1",
            request.requestUrl!!.encodedPath,
        )
        assertEquals(AlbumResource(), ArrJson.decodeFromString<AlbumResource>(
            request.body.readUtf8()))
    }

    @Test
    fun deleteAlbumHappyPath() = runTest {
        val fixture = fixture("album/deleteAlbum.json")
        server.enqueue(MockResponse().setBody(fixture))
        val actual = api.deleteAlbum(
            id = 1,
            deleteFiles = true,
            addImportListExclusion = true,
        )
        assertEquals(ArrJson.decodeFromString<JsonObject>(fixture), actual)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals(
            "/lidarr/api/v1/album/1",
            request.requestUrl!!.encodedPath,
        )
        assertEquals("true", request.requestUrl!!
            .queryParameter("deleteFiles"))
        assertEquals("true", request.requestUrl!!
            .queryParameter("addImportListExclusion"))
    }

    @Test
    fun getAlbumHappyPath() = runTest {
        val fixture = fixture("album/getAlbum.json")
        server.enqueue(MockResponse().setBody(fixture))
        val actual = api.getAlbum(
            id = 1,
        )
        assertEquals(ArrJson.decodeFromString<AlbumResource>(fixture), actual)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals(
            "/lidarr/api/v1/album/1",
            request.requestUrl!!.encodedPath,
        )
    }

    @Test
    fun monitorAlbumsHappyPath() = runTest {
        val fixture = fixture("album/monitorAlbums.json")
        server.enqueue(MockResponse().setBody(fixture))
        val actual = api.monitorAlbums(
            body = AlbumsMonitoredResource(),
        )
        assertEquals(ArrJson.decodeFromString<JsonObject>(fixture), actual)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals(
            "/lidarr/api/v1/album/monitor",
            request.requestUrl!!.encodedPath,
        )
        assertEquals(AlbumsMonitoredResource(), ArrJson.decodeFromString<AlbumsMonitoredResource>(
            request.body.readUtf8()))
    }

    @Test
    fun httpErrorIsPreserved() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        try {
            api.getAlbums()
            fail("Expected HttpException")
        } catch (error: HttpException) {
            assertEquals(503, error.code())
        }
    }
}
