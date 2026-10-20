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

class ArtistLookupLibraryApiTest {
    private val server = MockWebServer()
    private lateinit var api: ArtistLookupApi

    @Before
    fun setUp() {
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/lidarr/"))
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        api = LidarrLibraryApis.artistLookup(retrofit)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun fixture(path: String): String = checkNotNull(
        javaClass.getResourceAsStream("/fixtures/lidarr/$path")
    ).bufferedReader().use { it.readText() }

    @Test
    fun lookupArtistsHappyPath() = runTest {
        val fixture = fixture("artistlookup/lookupArtists.json")
        server.enqueue(MockResponse().setBody(fixture))
        val actual = api.lookupArtists(
            term = "1",
        )
        assertEquals(ArrJson.decodeFromString<List<ArtistResource>>(fixture), actual)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals(
            "/lidarr/api/v1/artist/lookup",
            request.requestUrl!!.encodedPath,
        )
        assertEquals("1", request.requestUrl!!
            .queryParameter("term"))
    }

    @Test
    fun httpErrorIsPreserved() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        try {
            api.lookupArtists()
            fail("Expected HttpException")
        } catch (error: HttpException) {
            assertEquals(503, error.code())
        }
    }
}
