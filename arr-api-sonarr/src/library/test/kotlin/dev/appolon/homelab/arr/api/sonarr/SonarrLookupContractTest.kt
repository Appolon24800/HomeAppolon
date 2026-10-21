package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
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

class SonarrLookupContractTest {
    private fun fixture(): String = checkNotNull(javaClass.classLoader!!.getResource(
        "fixtures/sonarr/series/lookup.json"
    )).readText()

    @Test
    fun lookupUsesSeriesFactoryAndDeploymentRoot() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val api = SonarrLibraryApis.series(Retrofit.Builder().baseUrl(server.url("/sonarr/"))
                .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
                .build())
            server.enqueue(MockResponse().setBody(fixture()))
            val actual = api.lookup("Example Series", 1)
            assertEquals(ArrJson.decodeFromString<List<SeriesResource>>(fixture()), actual)
            assertEquals(12345, actual.single().tvdbId)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/sonarr/api/v3/series/lookup", request.requestUrl!!.encodedPath)
            assertEquals("Example Series", request.requestUrl!!.queryParameter("term"))
            assertEquals("1", request.requestUrl!!.queryParameter("language"))
            server.enqueue(MockResponse().setBody("[]"))
            assertTrue(api.lookup().isEmpty())
            assertNull(server.takeRequest().requestUrl!!.query)
            server.enqueue(MockResponse().setResponseCode(503))
            try {
                api.lookup("example")
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
            server.takeRequest()
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun fakeLookupUsesCatalogAndErrorHook() = runTest {
        val calls = mutableListOf<String>()
        val fake = FakeSonarrLibrary(latency = { calls += it })
        fake.lookupCatalog += ArrJson.decodeFromString<List<SeriesResource>>(fixture())
        assertEquals(fake.lookupCatalog, fake.series.lookup("example", 1))
        assertEquals(fake.lookupCatalog, fake.series.lookup("tvdb:12345"))
        assertTrue(fake.series.lookup("missing").isEmpty())
        assertTrue(fake.seriesStore.isEmpty())
        assertEquals(List(3) { "Series.lookup" }, calls)
        val failure = IllegalStateException("offline")
        fake.error = { failure }
        try {
            fake.series.lookup("example")
            fail("Expected injected error")
        } catch (error: IllegalStateException) {
            assertSame(failure, error)
        }
    }
}
