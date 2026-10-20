package dev.appolon.homelab.arr.api.sonarr

import java.io.IOException
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeFileListResource
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeFileResource
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.api.sonarr.model.EpisodesMonitoredResource
import dev.appolon.homelab.arr.api.sonarr.model.SeasonPassResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesEditorResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesStatusType

/** Contract tests use synthetic schema-derived fixtures, never live server data. */
@Suppress("DEPRECATION")
class SonarrLibraryApisTest {
    private fun fixture(path: String): String = checkNotNull(
        javaClass.classLoader?.getResourceAsStream("fixtures/sonarr/$path.json")
    ).bufferedReader().use { it.readText() }

    private fun retrofit(server: MockWebServer): Retrofit = Retrofit.Builder()
        .baseUrl(server.url("/sonarr/"))
        .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
        .build()

    @Test fun seriesListHappyPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody(fixture("series/list")))
            val api = SonarrLibraryApis.series(retrofit(server))
            val result = api.list(tvdbId = 1, includeSeasonImages = true)
            assertNotNull(result)
            assertEquals(1, result.size)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/sonarr/api/v3/series", request.requestUrl!!.encodedPath)
            assertEquals(listOf("1"), request.requestUrl!!.queryParameterValues("tvdbId"))
            assertEquals(
                listOf("true"), request.requestUrl!!.queryParameterValues("includeSeasonImages")
            )
        }
    }

    @Test fun seriesCreateHappyPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody(fixture("series/create")))
            val api = SonarrLibraryApis.series(retrofit(server))
            val result = api.create(body = SeriesResource())
            assertNotNull(result)
            assertEquals(1, result.id)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/sonarr/api/v3/series", request.requestUrl!!.encodedPath)
            assertTrue(request.body.size > 0)
        }
    }

    @Test fun seriesGetHappyPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody(fixture("series/get")))
            val api = SonarrLibraryApis.series(retrofit(server))
            val result = api.get(id = 1, includeSeasonImages = true)
            assertNotNull(result)
            assertEquals(1, result.id)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/sonarr/api/v3/series/1", request.requestUrl!!.encodedPath)
            assertEquals(
                listOf("true"), request.requestUrl!!.queryParameterValues("includeSeasonImages")
            )
        }
    }

    @Test fun seriesUpdateHappyPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody(fixture("series/update")))
            val api = SonarrLibraryApis.series(retrofit(server))
            val result = api.update(moveFiles = true, id = "1", body = SeriesResource())
            assertNotNull(result)
            assertEquals(1, result.id)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/sonarr/api/v3/series/1", request.requestUrl!!.encodedPath)
            assertEquals(listOf("true"), request.requestUrl!!.queryParameterValues("moveFiles"))
            assertTrue(request.body.size > 0)
        }
    }

    @Test fun seriesDeleteHappyPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody(fixture("series/delete")))
            val api = SonarrLibraryApis.series(retrofit(server))
            val result = api.delete(id = 1, deleteFiles = true, addImportListExclusion = true)
            assertNotNull(result)
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/sonarr/api/v3/series/1", request.requestUrl!!.encodedPath)
            assertEquals(listOf("true"), request.requestUrl!!.queryParameterValues("deleteFiles"))
            assertEquals(
                listOf("true"), request.requestUrl!!.queryParameterValues("addImportListExclusion")
            )
        }
    }

    @Test fun seriesErrorPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(500))
            val api = SonarrLibraryApis.series(retrofit(server))
            try {
                api.list()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(500, error.code())
            }
        }
    }

    @Test fun seriesEditorUpdateHappyPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody(fixture("serieseditor/update")))
            val api = SonarrLibraryApis.seriesEditor(retrofit(server))
            val result = api.update(body = SeriesEditorResource())
            assertNotNull(result)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/sonarr/api/v3/series/editor", request.requestUrl!!.encodedPath)
            assertTrue(request.body.size > 0)
        }
    }

    @Test fun seriesEditorDeleteHappyPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody(fixture("serieseditor/delete")))
            val api = SonarrLibraryApis.seriesEditor(retrofit(server))
            val result = api.delete(body = SeriesEditorResource())
            assertNotNull(result)
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/sonarr/api/v3/series/editor", request.requestUrl!!.encodedPath)
            assertTrue(request.body.size > 0)
        }
    }

    @Test fun seriesEditorErrorPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(500))
            val api = SonarrLibraryApis.seriesEditor(retrofit(server))
            try {
                api.update(body = SeriesEditorResource())
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(500, error.code())
            }
        }
    }

    @Test fun seriesFolderGetHappyPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody(fixture("seriesfolder/get")))
            val api = SonarrLibraryApis.seriesFolder(retrofit(server))
            val result = api.get(id = 1)
            assertNotNull(result)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/sonarr/api/v3/series/1/folder", request.requestUrl!!.encodedPath)
        }
    }

    @Test fun seriesFolderErrorPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(500))
            val api = SonarrLibraryApis.seriesFolder(retrofit(server))
            try {
                api.get(id = 1)
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(500, error.code())
            }
        }
    }

    @Test fun seriesImportCreateHappyPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody(fixture("seriesimport/create")))
            val api = SonarrLibraryApis.seriesImport(retrofit(server))
            val result = api.create(body = listOf(SeriesResource(id = 1)))
            assertNotNull(result)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/sonarr/api/v3/series/import", request.requestUrl!!.encodedPath)
            assertTrue(request.body.size > 0)
        }
    }

    @Test fun seriesImportErrorPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(500))
            val api = SonarrLibraryApis.seriesImport(retrofit(server))
            try {
                api.create(body = listOf(SeriesResource()))
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(500, error.code())
            }
        }
    }

    @Test fun seasonPassCreateHappyPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody(fixture("seasonpass/create")))
            val api = SonarrLibraryApis.seasonPass(retrofit(server))
            val result = api.create(body = SeasonPassResource())
            assertNotNull(result)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/sonarr/api/v3/seasonpass", request.requestUrl!!.encodedPath)
            assertTrue(request.body.size > 0)
        }
    }

    @Test fun seasonPassErrorPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(500))
            val api = SonarrLibraryApis.seasonPass(retrofit(server))
            try {
                api.create(body = SeasonPassResource())
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(500, error.code())
            }
        }
    }

    @Test fun episodeListHappyPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody(fixture("episode/list")))
            val api = SonarrLibraryApis.episode(retrofit(server))
            val result = api.list(
                seriesId = 1,
                seasonNumber = 1,
                episodeIds = listOf(1, 2),
                episodeFileId = 1,
                includeSeries = true,
                includeEpisodeFile = true,
                includeImages = true
            )
            assertNotNull(result)
            assertEquals(1, result.size)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/sonarr/api/v3/episode", request.requestUrl!!.encodedPath)
            assertEquals(listOf("1"), request.requestUrl!!.queryParameterValues("seriesId"))
            assertEquals(listOf("1"), request.requestUrl!!.queryParameterValues("seasonNumber"))
            assertEquals(listOf("1", "2"), request.requestUrl!!.queryParameterValues("episodeIds"))
            assertEquals(listOf("1"), request.requestUrl!!.queryParameterValues("episodeFileId"))
            assertEquals(listOf("true"), request.requestUrl!!.queryParameterValues("includeSeries"))
            assertEquals(
                listOf("true"), request.requestUrl!!.queryParameterValues("includeEpisodeFile")
            )
            assertEquals(listOf("true"), request.requestUrl!!.queryParameterValues("includeImages"))
        }
    }

    @Test fun episodeUpdateHappyPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody(fixture("episode/update")))
            val api = SonarrLibraryApis.episode(retrofit(server))
            val result = api.update(id = 1, body = EpisodeResource())
            assertNotNull(result)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/sonarr/api/v3/episode/1", request.requestUrl!!.encodedPath)
            assertTrue(request.body.size > 0)
        }
    }

    @Test fun episodeGetHappyPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody(fixture("episode/get")))
            val api = SonarrLibraryApis.episode(retrofit(server))
            val result = api.get(id = 1)
            assertNotNull(result)
            assertTrue(result.isSuccessful)
            assertNotNull(result.body())
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/sonarr/api/v3/episode/1", request.requestUrl!!.encodedPath)
        }
    }

    @Test fun episodeUpdateMonitorHappyPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody(fixture("episode/updateMonitor")))
            val api = SonarrLibraryApis.episode(retrofit(server))
            val result = api.updateMonitor(includeImages = true, body = EpisodesMonitoredResource())
            assertNotNull(result)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/sonarr/api/v3/episode/monitor", request.requestUrl!!.encodedPath)
            assertEquals(listOf("true"), request.requestUrl!!.queryParameterValues("includeImages"))
            assertTrue(request.body.size > 0)
        }
    }

    @Test fun episodeErrorPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(500))
            val api = SonarrLibraryApis.episode(retrofit(server))
            try {
                api.list()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(500, error.code())
            }
        }
    }

    @Test fun episodeFileListHappyPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody(fixture("episodefile/list")))
            val api = SonarrLibraryApis.episodeFile(retrofit(server))
            val result = api.list(seriesId = 1, episodeFileIds = listOf(1, 2))
            assertNotNull(result)
            assertEquals(1, result.size)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/sonarr/api/v3/episodefile", request.requestUrl!!.encodedPath)
            assertEquals(listOf("1"), request.requestUrl!!.queryParameterValues("seriesId"))
            assertEquals(
                listOf("1", "2"), request.requestUrl!!.queryParameterValues("episodeFileIds")
            )
        }
    }

    @Test fun episodeFileUpdateHappyPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody(fixture("episodefile/update")))
            val api = SonarrLibraryApis.episodeFile(retrofit(server))
            val result = api.update(id = "1", body = EpisodeFileResource())
            assertNotNull(result)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/sonarr/api/v3/episodefile/1", request.requestUrl!!.encodedPath)
            assertTrue(request.body.size > 0)
        }
    }

    @Test fun episodeFileDeleteHappyPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody(fixture("episodefile/delete")))
            val api = SonarrLibraryApis.episodeFile(retrofit(server))
            val result = api.delete(id = 1)
            assertNotNull(result)
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/sonarr/api/v3/episodefile/1", request.requestUrl!!.encodedPath)
        }
    }

    @Test fun episodeFileGetHappyPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody(fixture("episodefile/get")))
            val api = SonarrLibraryApis.episodeFile(retrofit(server))
            val result = api.get(id = 1)
            assertNotNull(result)
            assertTrue(result.isSuccessful)
            assertNotNull(result.body())
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/sonarr/api/v3/episodefile/1", request.requestUrl!!.encodedPath)
        }
    }

    @Test fun episodeFileUpdateEditorHappyPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody(fixture("episodefile/updateEditor")))
            val api = SonarrLibraryApis.episodeFile(retrofit(server))
            val result = api.updateEditor(body = EpisodeFileListResource())
            assertNotNull(result)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/sonarr/api/v3/episodefile/editor", request.requestUrl!!.encodedPath)
            assertTrue(request.body.size > 0)
        }
    }

    @Test fun episodeFileDeleteBulkHappyPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody(fixture("episodefile/deleteBulk")))
            val api = SonarrLibraryApis.episodeFile(retrofit(server))
            val result = api.deleteBulk(body = EpisodeFileListResource())
            assertNotNull(result)
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/sonarr/api/v3/episodefile/bulk", request.requestUrl!!.encodedPath)
            assertTrue(request.body.size > 0)
        }
    }

    @Test fun episodeFileUpdateBulkHappyPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody(fixture("episodefile/updateBulk")))
            val api = SonarrLibraryApis.episodeFile(retrofit(server))
            val result = api.updateBulk(body = listOf(EpisodeFileResource(id = 1)))
            assertNotNull(result)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/sonarr/api/v3/episodefile/bulk", request.requestUrl!!.encodedPath)
            assertTrue(request.body.size > 0)
        }
    }

    @Test fun episodeFileErrorPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(500))
            val api = SonarrLibraryApis.episodeFile(retrofit(server))
            try {
                api.list()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(500, error.code())
            }
        }
    }

    @Test fun renameEpisodeListHappyPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody(fixture("renameepisode/list")))
            val api = SonarrLibraryApis.renameEpisode(retrofit(server))
            val result = api.list(seriesId = 1, seasonNumber = 1)
            assertNotNull(result)
            assertEquals(1, result.size)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/sonarr/api/v3/rename", request.requestUrl!!.encodedPath)
            assertEquals(listOf("1"), request.requestUrl!!.queryParameterValues("seriesId"))
            assertEquals(listOf("1"), request.requestUrl!!.queryParameterValues("seasonNumber"))
        }
    }

    @Test fun renameEpisodeListBulkHappyPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody(fixture("renameepisode/listBulk")))
            val api = SonarrLibraryApis.renameEpisode(retrofit(server))
            val result = api.listBulk(seriesIds = listOf(1, 2))
            assertNotNull(result)
            assertEquals(1, result.size)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/sonarr/api/v3/rename/bulk", request.requestUrl!!.encodedPath)
            assertEquals(listOf("1", "2"), request.requestUrl!!.queryParameterValues("seriesIds"))
        }
    }

    @Test fun renameEpisodeErrorPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(500))
            val api = SonarrLibraryApis.renameEpisode(retrofit(server))
            try {
                api.list()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(500, error.code())
            }
        }
    }

    @Test fun mediaCoverGetHappyPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody(fixture("mediacover/get")))
            val api = SonarrLibraryApis.mediaCover(retrofit(server))
            val result = api.get(seriesId = 1, filename = "poster.jpg")
            assertNotNull(result)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/sonarr/api/v3/mediacover/1/poster.jpg", request.requestUrl!!.encodedPath)
        }
    }

    @Test fun mediaCoverErrorPath() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(500))
            val api = SonarrLibraryApis.mediaCover(retrofit(server))
            try {
                api.get(seriesId = 1, filename = "poster.jpg")
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(500, error.code())
            }
        }
    }

    @Test fun fakeStateLatencyAndErrorInjection() = runTest {
        val calls = mutableListOf<String>()
        val fake = FakeSonarrLibrary(latency = { calls += it })
        val created = fake.series.create(SeriesResource(title = "example"))
        assertEquals(created, fake.series.get(created.id))
        fake.episodeStore[1] = EpisodeResource(id = 1)
        fake.episode.updateMonitor(body = EpisodesMonitoredResource(listOf(1), true))
        assertTrue(fake.episode.get(1).body()!!.monitored)
        assertEquals(404, fake.episodeFile.get(404).code())
        fake.error = { IOException(it) }
        try {
            fake.series.list()
            fail("Expected injected failure")
        } catch (error: IOException) {
            assertEquals("Series.list", error.message)
        }
        assertTrue(calls.contains("Series.create"))
    }

    @Test fun missingEpisodeAndEpisodeFilePreserveNotFoundStatus() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setResponseCode(404))
            val retrofit = retrofit(server)
            assertEquals(404, SonarrLibraryApis.episode(retrofit).get(1).code())
            assertEquals(404, SonarrLibraryApis.episodeFile(retrofit).get(1).code())
        }
    }

    @Test fun dateStringsAndUnknownEnumValuesUseSharedJsonPolicy() {
        val series = ArrJson.decodeFromString<SeriesResource>(
            """{"added":"2026-01-01T02:03:04+05:30","status":"future-status","extra":1}"""
        )
        assertEquals("2026-01-01T02:03:04+05:30", series.added)
        assertEquals(SeriesStatusType.CONTINUING, series.status)
        val episode = ArrJson.decodeFromString<EpisodeResource>(
            """{"airDateUtc":"2026-01-02T01:02:03.1234567Z","finaleType":"future-finale"}"""
        )
        assertEquals("2026-01-02T01:02:03.1234567Z", episode.airDateUtc)
        assertEquals("future-finale", episode.finaleType)
        val file = ArrJson.decodeFromString<EpisodeFileResource>(
            """{"size":9007199254740991,"dateAdded":"2026-01-01T00:00:00Z"}"""
        )
        assertEquals(9007199254740991L, file.size)
        assertEquals("2026-01-01T00:00:00Z", file.dateAdded)
    }
}
