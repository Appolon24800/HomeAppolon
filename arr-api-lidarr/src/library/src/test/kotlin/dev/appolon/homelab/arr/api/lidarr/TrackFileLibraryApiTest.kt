package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.TrackFileListResource
import dev.appolon.homelab.arr.api.lidarr.model.TrackFileResource
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

class TrackFileLibraryApiTest {
    private val server = MockWebServer()
    private lateinit var api: TrackFileApi

    @Before
    fun setUp() {
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/lidarr/"))
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        api = LidarrLibraryApis.trackFile(retrofit)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun fixture(path: String): String = checkNotNull(
        javaClass.getResourceAsStream("/fixtures/lidarr/$path")
    ).bufferedReader().use { it.readText() }

    @Test
    fun getTrackFileHappyPath() = runTest {
        val fixture = fixture("trackfile/getTrackFile.json")
        server.enqueue(MockResponse().setBody(fixture))
        val actual = api.getTrackFile(
            id = 1,
        )
        assertEquals(ArrJson.decodeFromString<TrackFileResource>(fixture), actual)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals(
            "/lidarr/api/v1/trackfile/1",
            request.requestUrl!!.encodedPath,
        )
    }

    @Test
    fun updateTrackFileHappyPath() = runTest {
        val fixture = fixture("trackfile/updateTrackFile.json")
        server.enqueue(MockResponse().setBody(fixture))
        val actual = api.updateTrackFile(
            id = "1",
            body = TrackFileResource(),
        )
        assertEquals(ArrJson.decodeFromString<TrackFileResource>(fixture), actual)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals(
            "/lidarr/api/v1/trackfile/1",
            request.requestUrl!!.encodedPath,
        )
        assertEquals(TrackFileResource(), ArrJson.decodeFromString<TrackFileResource>(
            request.body.readUtf8()))
    }

    @Test
    fun deleteTrackFileHappyPath() = runTest {
        val fixture = fixture("trackfile/deleteTrackFile.json")
        server.enqueue(MockResponse().setBody(fixture))
        val actual = api.deleteTrackFile(
            id = 1,
        )
        assertEquals(ArrJson.decodeFromString<JsonObject>(fixture), actual)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals(
            "/lidarr/api/v1/trackfile/1",
            request.requestUrl!!.encodedPath,
        )
    }

    @Test
    fun getTrackFilesHappyPath() = runTest {
        val fixture = fixture("trackfile/getTrackFiles.json")
        server.enqueue(MockResponse().setBody(fixture))
        val actual = api.getTrackFiles(
            artistId = 1,
            trackFileIds = listOf(1, 2),
            albumId = listOf(1, 2),
            unmapped = true,
        )
        assertEquals(ArrJson.decodeFromString<List<TrackFileResource>>(fixture), actual)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals(
            "/lidarr/api/v1/trackfile",
            request.requestUrl!!.encodedPath,
        )
        assertEquals("1", request.requestUrl!!
            .queryParameter("artistId"))
        assertEquals(listOf("1", "2"), request.requestUrl!!
            .queryParameterValues("trackFileIds"))
        assertEquals(listOf("1", "2"), request.requestUrl!!
            .queryParameterValues("albumId"))
        assertEquals("true", request.requestUrl!!
            .queryParameter("unmapped"))
    }

    @Test
    fun updateTrackFilesHappyPath() = runTest {
        val fixture = fixture("trackfile/updateTrackFiles.json")
        server.enqueue(MockResponse().setBody(fixture))
        val actual = api.updateTrackFiles(
            body = TrackFileListResource(),
        )
        assertEquals(ArrJson.decodeFromString<JsonObject>(fixture), actual)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals(
            "/lidarr/api/v1/trackfile/editor",
            request.requestUrl!!.encodedPath,
        )
        assertEquals(TrackFileListResource(), ArrJson.decodeFromString<TrackFileListResource>(
            request.body.readUtf8()))
    }

    @Test
    fun deleteTrackFilesHappyPath() = runTest {
        val fixture = fixture("trackfile/deleteTrackFiles.json")
        server.enqueue(MockResponse().setBody(fixture))
        val actual = api.deleteTrackFiles(
            body = TrackFileListResource(),
        )
        assertEquals(ArrJson.decodeFromString<JsonObject>(fixture), actual)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals(
            "/lidarr/api/v1/trackfile/bulk",
            request.requestUrl!!.encodedPath,
        )
        assertEquals(TrackFileListResource(), ArrJson.decodeFromString<TrackFileListResource>(
            request.body.readUtf8()))
    }

    @Test
    fun httpErrorIsPreserved() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        try {
            api.getTrackFile(id = 1)
            fail("Expected HttpException")
        } catch (error: HttpException) {
            assertEquals(503, error.code())
        }
    }
}
