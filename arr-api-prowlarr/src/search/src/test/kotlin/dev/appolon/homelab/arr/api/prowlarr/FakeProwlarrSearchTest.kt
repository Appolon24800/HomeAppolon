package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.CommandResource
import dev.appolon.homelab.arr.api.prowlarr.model.CustomFilterResource
import dev.appolon.homelab.arr.api.prowlarr.model.HistoryEventType
import dev.appolon.homelab.arr.api.prowlarr.model.HistoryResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerCategory
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerStatsResource
import dev.appolon.homelab.arr.api.prowlarr.model.ReleaseResource
import dev.appolon.homelab.arr.api.prowlarr.model.SortDirection
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException

/** Drives the fake the way UI tests will: seeded state, CRUD, injections. */
class FakeProwlarrSearchTest {
    private val movies = ReleaseResource(
        id = 1,
        title = "Spec Release 1080p",
        indexerId = 1,
        categories = listOf(IndexerCategory(id = 2000)),
    )
    private val tv = ReleaseResource(
        id = 2,
        title = "Spec Show S01 720p",
        indexerId = 2,
        categories = listOf(IndexerCategory(id = 5000)),
    )

    @Test
    fun searchFiltersPageAndGrabs() = runTest {
        val fake = FakeProwlarrSearch()
        fake.releases += listOf(movies, tv)

        assertEquals(listOf(1, 2), fake.getSearch("spec", null, null, null, null, null).map { it.id })
        assertEquals(listOf(2), fake.getSearch(null, null, listOf(2), null, null, null).map { it.id })
        assertEquals(listOf(1), fake.getSearch(null, null, null, listOf(2000), null, null).map { it.id })
        assertEquals(
            listOf(2),
            fake.getSearch("Spec", null, null, null, 1, 1).map { it.id },
        )

        fake.createSearch(movies)
        fake.createSearchBulk(listOf(tv, movies))
        assertEquals(listOf(1, 2, 1), fake.grabs.map { it.id })
    }

    @Test
    fun newznabAndDownloadServeSeededBodies() = runTest {
        val fake = FakeProwlarrSearch()
        fake.newznabBody = "<rss version=\"2.0\">spec</rss>"
        fake.downloadBody = byteArrayOf(1, 2, 3)

        assertEquals(fake.newznabBody, fake.getNewznab(
            id = 1, t = null, q = null, cat = null, imdbid = null, tmdbid = null,
            extended = null, limit = null, offset = null, minage = null, maxage = null,
            minsize = null, maxsize = null, rid = null, tvmazeid = null, traktid = null,
            tvdbid = null, doubanid = null, season = null, ep = null, album = null,
            artist = null, label = null, track = null, year = null, genre = null,
            author = null, title = null, publisher = null, configured = null,
            source = null, host = null, server = null,
        ).string())
        assertEquals(
            fake.newznabBody,
            fake.getNewznabLegacy(
                id = 1, t = null, q = null, cat = null, imdbid = null, tmdbid = null,
                extended = null, limit = null, offset = null, minage = null, maxage = null,
                minsize = null, maxsize = null, rid = null, tvmazeid = null, traktid = null,
                tvdbid = null, doubanid = null, season = null, ep = null, album = null,
                artist = null, label = null, track = null, year = null, genre = null,
                author = null, title = null, publisher = null, configured = null,
                source = null, host = null, server = null,
            ).string(),
        )
        val download = fake.getDownload(1, "https://example.org/1", "file.nzb")
        assertTrue(download.bytes().contentEquals(fake.downloadBody))
        assertEquals(3, fake.getDownloadLegacy(1, null, null).bytes().size)
    }

    @Test
    fun historyFiltersPageAndSorts() = runTest {
        val fake = FakeProwlarrSearch()
        fake.history += listOf(
            HistoryResource(id = 1, indexerId = 1, date = "2026-01-15T10:30:00Z",
                successful = true, eventType = HistoryEventType.RELEASE_GRABBED),
            HistoryResource(id = 2, indexerId = 2, date = "2026-01-16T09:00:00Z",
                successful = true, eventType = HistoryEventType.INDEXER_QUERY),
            HistoryResource(id = 3, indexerId = 1, date = "2026-01-17T12:45:00Z",
                successful = false, eventType = HistoryEventType.INDEXER_AUTH),
        )

        val paged = fake.getHistory(1, 2, null, null, null, null, null, null)
        assertEquals(3, paged.totalRecords)
        assertEquals(listOf(1, 2), paged.records.orEmpty().map { it.id })

        val newestFirst = fake.getHistory(
            1, 10, "date", SortDirection.DESCENDING, null, null, null, null
        )
        assertEquals(listOf(3, 2, 1), newestFirst.records.orEmpty().map { it.id })

        val onlyQueries = fake.getHistory(
            null, null, null, null, listOf(HistoryEventType.INDEXER_QUERY.ordinal),
            null, null, null
        )
        assertEquals(listOf(2), onlyQueries.records.orEmpty().map { it.id })

        val since = fake.getHistorySince("2026-01-16T00:00:00Z", null)
        assertEquals(listOf(2, 3), since.map { it.id })

        val perIndexer = fake.getHistoryForIndexer(1, HistoryEventType.RELEASE_GRABBED, null)
        assertEquals(listOf(1), perIndexer.map { it.id })
    }

    @Test
    fun statsCustomFiltersAndCommandsBehaveLikeTheServer() = runTest {
        val fake = FakeProwlarrSearch()
        fake.indexerStats = IndexerStatsResource(id = 1)
        assertEquals(1, fake.getIndexerStats(null, null, null, null, null).id)

        val created = fake.createCustomFilter(CustomFilterResource(label = "Spec"))
        assertTrue(created.id != 0)
        assertEquals("Spec", fake.getCustomFilter(created.id).label)
        val updated = fake.updateCustomFilter(created.id.toString(), created.copy(label = "Renamed"))
        assertEquals("Renamed", fake.customFilters.getValue(created.id).label)
        assertTrue(fake.deleteCustomFilter(created.id).isSuccessful)
        assertEquals(0, fake.customFilters.size)

        val command = fake.createCommand(CommandResource(name = "ApplicationIndexerSync"))
        assertEquals(command.id, fake.getCommand(command.id).id)
        assertEquals(1, fake.getCommands().size)
        assertTrue(fake.deleteCommand(command.id).isSuccessful)
        assertEquals(0, fake.getCommands().size)
    }

    @Test
    fun unknownReadsFailWith404() = runTest {
        val fake = FakeProwlarrSearch()
        val error = runCatching { fake.getCustomFilter(9) }.exceptionOrNull()
        assertTrue(error is HttpException)
        assertEquals(404, (error as HttpException).code())
        val commandError = runCatching { fake.getCommand(9) }.exceptionOrNull()
        assertTrue(commandError is HttpException)
        assertEquals(404, (commandError as HttpException).code())
    }

    @Test
    fun latencyFailureAndErrorInjection() = runTest {
        val fake = FakeProwlarrSearch()
        fake.releases += movies
        var waited = false
        fake.latency = { waited = true }
        fake.getSearch(null, null, null, null, null, null)
        assertTrue(waited)

        fake.failure = { java.io.IOException("transport gone") }
        val transport = runCatching { fake.getSearch(null, null, null, null, null, null) }
            .exceptionOrNull()
        assertTrue(transport is java.io.IOException)

        fake.failure = { null }
        fake.errorStatus = { 503 }
        val plainError = runCatching { fake.getSearch(null, null, null, null, null, null) }
            .exceptionOrNull()
        assertTrue(plainError is HttpException)
        assertEquals(503, (plainError as HttpException).code())
        assertEquals(503, fake.deleteCommand(1).code())
    }

    @Test
    fun recordsEveryCallWithArguments() = runTest {
        val fake = FakeProwlarrSearch()
        fake.getDownload(2, "https://example.org/1", "file.nzb")
        val call = fake.calls.single()
        assertEquals("getDownload", call.operation)
        assertEquals(2, call.arguments["id"])
        assertEquals("file.nzb", call.arguments["file"])
    }
}
