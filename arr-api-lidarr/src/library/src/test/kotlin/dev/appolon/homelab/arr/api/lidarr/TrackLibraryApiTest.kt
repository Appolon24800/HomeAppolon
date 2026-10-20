package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.TrackResource
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

class TrackLibraryApiTest {
    private val server = MockWebServer()
    private lateinit var api: TrackApi

    @Before
    fun setUp() {
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/lidarr/"))
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        api = LidarrLibraryApis.track(retrofit)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun fixture(path: String): String = checkNotNull(
        javaClass.getResourceAsStream("/fixtures/lidarr/$path")
    ).bufferedReader().use { it.readText() }

    @Test
    fun getTracksHappyPath() = runTest {
        val fixture = fixture("track/getTracks.json")
        server.enqueue(MockResponse().setBody(fixture))
        val actual = api.getTracks(
            artistId = 1,
            albumId = 1,
            albumReleaseId = 1,
            trackIds = listOf(1, 2),
        )
        assertEquals(ArrJson.decodeFromString<List<TrackResource>>(fixture), actual)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals(
            "/lidarr/api/v1/track",
            request.requestUrl!!.encodedPath,
        )
        assertEquals("1", request.requestUrl!!
            .queryParameter("artistId"))
        assertEquals("1", request.requestUrl!!
            .queryParameter("albumId"))
        assertEquals("1", request.requestUrl!!
            .queryParameter("albumReleaseId"))
        assertEquals(listOf("1", "2"), request.requestUrl!!
            .queryParameterValues("trackIds"))
    }

    @Test
    fun getTrackHappyPath() = runTest {
        val fixture = fixture("track/getTrack.json")
        server.enqueue(MockResponse().setBody(fixture))
        val actual = api.getTrack(
            id = 1,
        )
        assertEquals(ArrJson.decodeFromString<TrackResource>(fixture), actual)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals(
            "/lidarr/api/v1/track/1",
            request.requestUrl!!.encodedPath,
        )
    }

    @Test
    fun httpErrorIsPreserved() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        try {
            api.getTracks()
            fail("Expected HttpException")
        } catch (error: HttpException) {
            assertEquals(503, error.code())
        }
    }
}
