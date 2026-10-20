package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.ArtistEditorResource
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

class ArtistEditorLibraryApiTest {
    private val server = MockWebServer()
    private lateinit var api: ArtistEditorApi

    @Before
    fun setUp() {
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/lidarr/"))
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        api = LidarrLibraryApis.artistEditor(retrofit)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun fixture(path: String): String = checkNotNull(
        javaClass.getResourceAsStream("/fixtures/lidarr/$path")
    ).bufferedReader().use { it.readText() }

    @Test
    fun updateArtistsHappyPath() = runTest {
        val fixture = fixture("artisteditor/updateArtists.json")
        server.enqueue(MockResponse().setBody(fixture))
        val actual = api.updateArtists(
            body = ArtistEditorResource(),
        )
        assertEquals(ArrJson.decodeFromString<JsonObject>(fixture), actual)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals(
            "/lidarr/api/v1/artist/editor",
            request.requestUrl!!.encodedPath,
        )
        assertEquals(ArtistEditorResource(), ArrJson.decodeFromString<ArtistEditorResource>(
            request.body.readUtf8()))
    }

    @Test
    fun deleteArtistsHappyPath() = runTest {
        val fixture = fixture("artisteditor/deleteArtists.json")
        server.enqueue(MockResponse().setBody(fixture))
        val actual = api.deleteArtists(
            body = ArtistEditorResource(),
        )
        assertEquals(ArrJson.decodeFromString<JsonObject>(fixture), actual)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals(
            "/lidarr/api/v1/artist/editor",
            request.requestUrl!!.encodedPath,
        )
        assertEquals(ArtistEditorResource(), ArrJson.decodeFromString<ArtistEditorResource>(
            request.body.readUtf8()))
    }

    @Test
    fun httpErrorIsPreserved() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        try {
            api.updateArtists(body = ArtistEditorResource())
            fail("Expected HttpException")
        } catch (error: HttpException) {
            assertEquals(503, error.code())
        }
    }
}
