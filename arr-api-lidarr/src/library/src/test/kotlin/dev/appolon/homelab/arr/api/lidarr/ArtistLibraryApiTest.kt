package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
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

class ArtistLibraryApiTest {
    private val server = MockWebServer()
    private lateinit var api: ArtistApi

    @Before
    fun setUp() {
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/lidarr/"))
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        api = LidarrLibraryApis.artist(retrofit)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun fixture(path: String): String = checkNotNull(
        javaClass.getResourceAsStream("/fixtures/lidarr/$path")
    ).bufferedReader().use { it.readText() }

    @Test
    fun getArtistHappyPath() = runTest {
        val fixture = fixture("artist/getArtist.json")
        server.enqueue(MockResponse().setBody(fixture))
        val actual = api.getArtist(
            id = 1,
        )
        assertEquals(ArrJson.decodeFromString<ArtistResource>(fixture), actual)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals(
            "/lidarr/api/v1/artist/1",
            request.requestUrl!!.encodedPath,
        )
    }

    @Test
    fun updateArtistHappyPath() = runTest {
        val fixture = fixture("artist/updateArtist.json")
        server.enqueue(MockResponse().setBody(fixture))
        val actual = api.updateArtist(
            id = "1",
            moveFiles = true,
            body = ArtistResource(),
        )
        assertEquals(ArrJson.decodeFromString<ArtistResource>(fixture), actual)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals(
            "/lidarr/api/v1/artist/1",
            request.requestUrl!!.encodedPath,
        )
        assertEquals("true", request.requestUrl!!
            .queryParameter("moveFiles"))
        assertEquals(ArtistResource(), ArrJson.decodeFromString<ArtistResource>(
            request.body.readUtf8()))
    }

    @Test
    fun deleteArtistHappyPath() = runTest {
        val fixture = fixture("artist/deleteArtist.json")
        server.enqueue(MockResponse().setBody(fixture))
        val actual = api.deleteArtist(
            id = 1,
            deleteFiles = true,
            addImportListExclusion = true,
        )
        assertEquals(ArrJson.decodeFromString<JsonObject>(fixture), actual)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals(
            "/lidarr/api/v1/artist/1",
            request.requestUrl!!.encodedPath,
        )
        assertEquals("true", request.requestUrl!!
            .queryParameter("deleteFiles"))
        assertEquals("true", request.requestUrl!!
            .queryParameter("addImportListExclusion"))
    }

    @Test
    fun getArtistsHappyPath() = runTest {
        val fixture = fixture("artist/getArtists.json")
        server.enqueue(MockResponse().setBody(fixture))
        val actual = api.getArtists(
            mbId = "1",
        )
        assertEquals(ArrJson.decodeFromString<List<ArtistResource>>(fixture), actual)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals(
            "/lidarr/api/v1/artist",
            request.requestUrl!!.encodedPath,
        )
        assertEquals("1", request.requestUrl!!
            .queryParameter("mbId"))
    }

    @Test
    fun addArtistHappyPath() = runTest {
        val fixture = fixture("artist/addArtist.json")
        server.enqueue(MockResponse().setBody(fixture))
        val actual = api.addArtist(
            body = ArtistResource(),
        )
        assertEquals(ArrJson.decodeFromString<ArtistResource>(fixture), actual)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals(
            "/lidarr/api/v1/artist",
            request.requestUrl!!.encodedPath,
        )
        assertEquals(ArtistResource(), ArrJson.decodeFromString<ArtistResource>(
            request.body.readUtf8()))
    }

    @Test
    fun httpErrorIsPreserved() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        try {
            api.getArtist(id = 1)
            fail("Expected HttpException")
        } catch (error: HttpException) {
            assertEquals(503, error.code())
        }
    }
}
