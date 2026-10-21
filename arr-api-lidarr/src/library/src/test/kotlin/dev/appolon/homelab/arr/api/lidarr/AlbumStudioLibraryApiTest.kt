package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.AlbumStudioResource
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

class AlbumStudioLibraryApiTest {
    private val server = MockWebServer()
    private lateinit var api: AlbumStudioApi

    @Before
    fun setUp() {
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/lidarr/"))
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        api = LidarrLibraryApis.albumStudio(retrofit)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun fixture(path: String): String = checkNotNull(
        javaClass.getResourceAsStream("/fixtures/lidarr/$path")
    ).bufferedReader().use { it.readText() }

    @Test
    fun updateAlbumStudioHappyPath() = runTest {
        val fixture = fixture("albumstudio/updateAlbumStudio.json")
        server.enqueue(MockResponse().setBody(fixture))
        val actual = api.updateAlbumStudio(
            body = AlbumStudioResource(),
        )
        assertEquals(ArrJson.decodeFromString<JsonObject>(fixture), actual)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals(
            "/lidarr/api/v1/albumstudio",
            request.requestUrl!!.encodedPath,
        )
        assertEquals(AlbumStudioResource(), ArrJson.decodeFromString<AlbumStudioResource>(
            request.body.readUtf8()))
    }

    @Test
    fun httpErrorIsPreserved() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        try {
            api.updateAlbumStudio(body = AlbumStudioResource())
            fail("Expected HttpException")
        } catch (error: HttpException) {
            assertEquals(503, error.code())
        }
    }
}
