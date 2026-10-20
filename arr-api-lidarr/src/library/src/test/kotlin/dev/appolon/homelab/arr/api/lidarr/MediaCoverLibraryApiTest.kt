package dev.appolon.homelab.arr.api.lidarr

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

class MediaCoverLibraryApiTest {
    private val server = MockWebServer()
    private lateinit var api: MediaCoverApi

    @Before
    fun setUp() {
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/lidarr/"))
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        api = LidarrLibraryApis.mediaCover(retrofit)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun fixture(path: String): String = checkNotNull(
        javaClass.getResourceAsStream("/fixtures/lidarr/$path")
    ).bufferedReader().use { it.readText() }

    @Test
    fun getArtistCoverHappyPath() = runTest {
        val fixture = fixture("mediacover/getArtistCover.json")
        server.enqueue(MockResponse().setBody(fixture))
        val actual = api.getArtistCover(
            artistId = 1,
            filename = "cover.jpg",
        )
        assertEquals(ArrJson.decodeFromString<JsonObject>(fixture), actual)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals(
            "/lidarr/api/v1/mediacover/artist/1/cover.jpg",
            request.requestUrl!!.encodedPath,
        )
    }

    @Test
    fun getAlbumCoverHappyPath() = runTest {
        val fixture = fixture("mediacover/getAlbumCover.json")
        server.enqueue(MockResponse().setBody(fixture))
        val actual = api.getAlbumCover(
            albumId = 1,
            filename = "cover.jpg",
        )
        assertEquals(ArrJson.decodeFromString<JsonObject>(fixture), actual)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals(
            "/lidarr/api/v1/mediacover/album/1/cover.jpg",
            request.requestUrl!!.encodedPath,
        )
    }

    @Test
    fun httpErrorIsPreserved() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        try {
            api.getArtistCover(artistId = 1, filename = "cover.jpg")
            fail("Expected HttpException")
        } catch (error: HttpException) {
            assertEquals(503, error.code())
        }
    }
}
