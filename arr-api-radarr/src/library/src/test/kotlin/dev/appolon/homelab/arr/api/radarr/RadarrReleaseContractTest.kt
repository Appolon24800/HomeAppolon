package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.fake.FakeRadarrLibrary
import dev.appolon.homelab.arr.api.radarr.model.ReleaseResource
import dev.appolon.homelab.arr.api.radarr.model.RenameMovieResource
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class RadarrReleaseContractTest {
    private fun fixture(name: String): String = checkNotNull(
        javaClass.classLoader!!.getResource("fixtures/radarr/release/$name.json")
    ).readText()

    @Test
    fun releaseAndRenameContracts() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val retrofit = Retrofit.Builder().baseUrl(server.url("/radarr/"))
                .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
                .build()
            val api = RadarrApis.release(retrofit)
            val json = fixture("release")
            val expected = ArrJson.decodeFromString<ReleaseResource>(json)
            server.enqueue(MockResponse().setBody("[$json]"))
            assertEquals(listOf(expected), api.list(7))
            val list = server.takeRequest()
            assertEquals("GET", list.method)
            assertEquals("/radarr/api/v3/release?movieId=7", list.path)
            assertEquals(5000000000L, expected.size)
            assertEquals(1234567, expected.imdbId)

            server.enqueue(MockResponse().setBody(json))
            assertEquals(expected, api.grab(expected).body())
            val grab = server.takeRequest()
            assertEquals("POST", grab.method)
            assertEquals("/radarr/api/v3/release", grab.path)
            assertEquals(expected, ArrJson.decodeFromString<ReleaseResource>(grab.body.readUtf8()))

            server.enqueue(MockResponse().setBody("[$json]"))
            assertEquals(listOf(expected), api.push(expected))
            val push = server.takeRequest()
            assertEquals("POST", push.method)
            assertEquals("/radarr/api/v3/release/push", push.path)
            assertEquals(expected, ArrJson.decodeFromString<ReleaseResource>(push.body.readUtf8()))

            server.enqueue(MockResponse().setBody(fixture("rename")))
            val renames = RadarrApis.movieFile(retrofit).rename(listOf(7, 8))
            assertEquals(ArrJson.decodeFromString<List<RenameMovieResource>>(fixture("rename")), renames)
            val rename = server.takeRequest()
            assertEquals("GET", rename.method)
            assertEquals("/radarr/api/v3/rename", rename.requestUrl!!.encodedPath)
            assertEquals(listOf("7", "8"), rename.requestUrl!!.queryParameterValues("movieId"))

            server.enqueue(MockResponse().setResponseCode(404))
            assertEquals(404, api.grab(expected).code())
            server.takeRequest()
            server.enqueue(MockResponse().setResponseCode(400))
            try {
                api.push(expected)
                fail("Expected HTTP 400")
            } catch (error: HttpException) {
                assertEquals(400, error.code())
            }
            server.takeRequest()
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun fakeReleaseAndRenameStateAndFailures() = runTest {
        var delayed = 0L
        val fake = FakeRadarrLibrary(latencyMs = 5, sleep = { delayed += it })
        val release = ArrJson.decodeFromString<ReleaseResource>(fixture("release"))
        fake.release.push(release)
        assertEquals(listOf(release), fake.release.list(7))
        assertTrue(fake.release.list(8).isEmpty())
        assertEquals(release, fake.release.grab(release).body())
        assertEquals(listOf(release), fake.grabbedReleases)
        fake.renames += ArrJson.decodeFromString<List<RenameMovieResource>>(fixture("rename"))
        assertEquals(listOf(7), fake.movieFile.rename(listOf(7)).map { it.movieId })
        assertEquals(2, fake.movieFile.rename().size)
        assertEquals(30L, delayed)
        val failure = IllegalStateException("offline")
        fake.errorOnce = failure
        try {
            fake.release.list()
            fail("Expected injected error")
        } catch (error: IllegalStateException) {
            assertSame(failure, error)
        }
        assertEquals(listOf(release), fake.release.list())
    }
}
