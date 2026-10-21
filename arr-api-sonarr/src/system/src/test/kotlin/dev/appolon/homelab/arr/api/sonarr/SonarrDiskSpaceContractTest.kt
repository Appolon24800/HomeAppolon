package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.DiskSpaceResource
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import okhttp3.MediaType.Companion.toMediaType
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

@Suppress("DEPRECATION")
class SonarrDiskSpaceContractTest {
    private val server = MockWebServer()
    private lateinit var api: DiskSpaceApi

    @Before
    fun setUp() {
        server.start()
        api = SonarrSystemApis.diskSpace(
            Retrofit.Builder().baseUrl(server.url("/sonarr/"))
                .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
                .build()
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun fixture(path: String): String =
        checkNotNull(javaClass.classLoader!!.getResource(path)).readText()

    @Test
    fun listHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/diskspace/list.json"))
        )
        val expected = ArrJson.decodeFromString<List<DiskSpaceResource>>(
            fixture("fixtures/sonarr/diskspace/list.json")
        )
        assertEquals(5368709120L, expected[0].freeSpace)
        assertEquals(expected, api.list())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/diskspace", request.requestUrl!!.encodedPath)
    }

    @Test
    fun httpError() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
        try {
            api.list()
            fail("Expected HTTP failure")
        } catch (error: HttpException) {
            assertEquals(500, error.code())
        }
    }
}
