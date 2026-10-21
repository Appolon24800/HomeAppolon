package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.BlocklistBulkResource
import dev.appolon.homelab.arr.api.radarr.model.BlocklistResource
import dev.appolon.homelab.arr.api.radarr.model.CommandResource
import dev.appolon.homelab.arr.api.radarr.model.CommandStatus
import dev.appolon.homelab.arr.api.radarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.radarr.model.HistoryResource
import dev.appolon.homelab.arr.api.radarr.model.Language
import dev.appolon.homelab.arr.api.radarr.model.ManualImportReprocessResource
import dev.appolon.homelab.arr.api.radarr.model.ManualImportResource
import dev.appolon.homelab.arr.api.radarr.model.MovieHistoryEventType
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.Quality
import dev.appolon.homelab.arr.api.radarr.model.QualityModel
import dev.appolon.homelab.arr.api.radarr.model.QueueBulkResource
import dev.appolon.homelab.arr.api.radarr.model.QueueResource
import dev.appolon.homelab.arr.api.radarr.model.QueueStatus
import dev.appolon.homelab.arr.api.radarr.model.SortDirection
import dev.appolon.homelab.arr.api.radarr.model.TrackedDownloadStatus
import dev.appolon.homelab.arr.net.ArrJson
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FakeRadarrQueueTest {
    @Test fun queueFiltersPagesAndActionsMutateState() = runTest {
        val fake = FakeRadarrQueue(now = { "2026-01-01T00:00:00Z" })
        fake.queueItems += listOf(
            QueueResource(id = 1, movieId = 1, size = 2.0),
            QueueResource(id = 2, movieId = 1, size = 12.0),
            QueueResource(id = 3, movieId = null),
        )
        val page = fake.queue(pageSize = 1, sortKey = "size",
            sortDirection = SortDirection.DESCENDING)
        assertEquals(2, page.totalRecords)
        assertEquals(2, page.records!!.single().id)
        assertEquals(3, fake.queue(includeUnknownMovieItems = true).totalRecords)
        assertEquals(2, fake.queueDetails(movieId = 1).size)
        assertEquals(3, fake.queueStatus().totalCount)
        assertEquals(404, fake.grabQueue(99).code())
        assertEquals(404, fake.grabQueueBulk(QueueBulkResource(listOf(1, 99))).code())
        assertTrue(fake.grabbedIds.isEmpty())
        assertEquals(200, fake.grabQueue(1).code())
        assertEquals(200, fake.grabQueueBulk(QueueBulkResource(listOf(2))).code())
        assertEquals(listOf(1, 2), fake.grabbedIds)
        assertEquals(200, fake.removeQueue(1, blocklist = true).code())
        assertEquals("2026-01-01T00:00:00Z", fake.blocklistItems.single().date)
        assertEquals(1, fake.blocklistItems.single().movieId)
        fake.removeQueueBulk(body = QueueBulkResource(listOf(2)))
        assertEquals(listOf(3), fake.queueItems.map { it.id })
        assertTrue(fake.removals.first().removeFromClient)
        assertFalse(fake.removals.first().skipRedownload)
        assertEquals(404, fake.removeQueue(1).code())
    }

    @Test fun queueFiltersAllCollectionAndEnumParameters() = runTest {
        val fake = FakeRadarrQueue()
        fake.queueItems += QueueResource(
            id = 1,
            movieId = 7,
            protocol = DownloadProtocol.TORRENT,
            languages = listOf(Language(id = 3)),
            quality = QualityModel(quality = Quality(id = 4)),
            status = QueueStatus.DOWNLOADING,
            trackedDownloadStatus = TrackedDownloadStatus.ERROR,
        )
        fake.queueItems += QueueResource(id = 2,
            trackedDownloadStatus = TrackedDownloadStatus.WARNING)
        assertEquals(1, fake.queue(movieIds = listOf(7), protocol = DownloadProtocol.TORRENT,
            languages = listOf(3), quality = listOf(4),
            status = listOf(QueueStatus.DOWNLOADING)).records!!.single().id)
        assertEquals(0, fake.queue(protocol = DownloadProtocol.USENET).totalRecords)
        assertEquals(0, fake.queue(languages = emptyList()).totalRecords)
        assertEquals(0, fake.queue(page = Int.MAX_VALUE).records!!.size)
        assertTrue(fake.queueStatus().errors)
        assertTrue(fake.queueStatus().unknownWarnings)
    }

    @Test fun blocklistFiltersAndDeletes() = runTest {
        val fake = FakeRadarrQueue()
        fake.blocklistItems += BlocklistResource(id = 1, movieId = 7,
            protocol = DownloadProtocol.TORRENT)
        fake.blocklistItems += BlocklistResource(id = 2, movieId = 8)
        assertEquals(1, fake.blocklist(movieIds = listOf(7),
            protocols = listOf(DownloadProtocol.TORRENT)).records!!.single().id)
        assertEquals(1, fake.movieBlocklist(7).size)
        fake.removeBlocklist(1)
        fake.removeBlocklistBulk(BlocklistBulkResource(listOf(2)))
        assertTrue(fake.blocklistItems.isEmpty())
    }

    @Test fun historyFiltersDatesAndMarksFailed() = runTest {
        val fake = FakeRadarrQueue(historyEventIds = mapOf(MovieHistoryEventType.GRABBED to 42))
        fake.historyItems += HistoryResource(id = 1, movieId = 7,
            date = "2026-01-01T00:00:00Z", downloadId = "download",
            eventType = MovieHistoryEventType.GRABBED)
        fake.historyItems += HistoryResource(id = 2, movieId = 8,
            date = "2026-01-02T00:00:00Z")
        assertEquals(1, fake.history(movieIds = listOf(7), downloadId = "download",
            eventType = listOf(42)).records!!.single().id)
        assertEquals(1, fake.movieHistory(7, MovieHistoryEventType.GRABBED).size)
        assertEquals(2, fake.historySince("2026-01-01T12:00:00Z").single().id)
        fake.markHistoryFailed(1)
        assertEquals(MovieHistoryEventType.DOWNLOAD_FAILED, fake.historyItems.first().eventType)
    }

    @Test fun manualImportReprocessesSeededFiles() = runTest {
        val fake = FakeRadarrQueue()
        fake.importItems += ManualImportResource(id = 1, path = "/downloads/movie/file.mkv",
            downloadId = "download", movie = MovieResource(id = 7))
        fake.importItems += ManualImportResource(id = 2, path = "/downloads/movie/old.mkv",
            movieFileId = 10)
        assertEquals(1, fake.manualImport(folder = "/downloads/movie", downloadId = "download",
            movieId = 7).single().id)
        assertEquals(2, fake.manualImport(filterExistingFiles = false).size)
        val revised = ManualImportReprocessResource(id = 1, releaseGroup = "string")
        assertEquals(listOf(revised), fake.reprocessManualImport(listOf(revised)).body())
        assertEquals("string", fake.importItems.first().releaseGroup)
    }

    @Test fun commandLifecycleKeepsCancelledCommands() = runTest {
        val fake = FakeRadarrQueue(now = { "2026-01-01T00:00:00Z" })
        val started = fake.startCommand(CommandResource(name = "RefreshMovie"))
        assertEquals(201, started.code())
        val command = started.body()!!
        assertEquals(command, fake.command(command.id).body())
        assertEquals(listOf(command), fake.commands())
        fake.cancelCommand(command.id)
        assertEquals(CommandStatus.CANCELLED, fake.command(command.id).body()!!.status)
        assertEquals(404, fake.command(999).code())
    }

    @Test fun everyOperationSupportsLatencyAndErrorInjectionBeforeMutation() = runTest {
        val failure = IOException("Injected offline failure")
        val called = mutableListOf<String>()
        val delays = mutableListOf<Long>()
        val fake = FakeRadarrQueue(
            latencyMs = 25,
            errorFor = { called += it; failure },
            sleep = { delays += it; delay(it) },
        )
        fake.queueItems += QueueResource(id = 1, movieId = 1)
        val operations: List<suspend () -> Any?> = listOf(
            { fake.queue() },
            { fake.removeQueue(1) },
            { fake.removeQueueBulk(body = QueueBulkResource(listOf(1))) },
            { fake.grabQueue(1) },
            { fake.grabQueueBulk(QueueBulkResource(listOf(1))) },
            { fake.queueDetails() },
            { fake.queueStatus() },
            { fake.blocklist() },
            { fake.movieBlocklist() },
            { fake.removeBlocklist(1) },
            { fake.removeBlocklistBulk(BlocklistBulkResource(listOf(1))) },
            { fake.history() },
            { fake.historySince() },
            { fake.movieHistory() },
            { fake.markHistoryFailed(1) },
            { fake.manualImport() },
            { fake.reprocessManualImport(emptyList()) },
            { fake.startCommand(CommandResource()) },
            { fake.commands() },
            { fake.command(1) },
            { fake.cancelCommand(1) },
        )
        operations.forEach { operation ->
            try {
                operation()
                fail("Expected injected failure")
            } catch (error: IOException) {
                assertSame(failure, error)
            }
        }
        assertEquals(21, called.distinct().size)
        assertEquals(List(21) { 25L }, delays)
        assertEquals(1, fake.queueItems.size)
        assertTrue(fake.removals.isEmpty())
        assertTrue(fake.grabbedIds.isEmpty())
        assertTrue(fake.commandItems.isEmpty())
    }

    @Test fun wireEnumsCoerceUnknownValuesAndDatesRemainUntouched() {
        val decoded = ArrJson.decodeFromString<QueueResource>(
            """{"id":1,"status":"futureStatus","protocol":"futureProtocol",
                "added":"not-a-parsed-date","sizeleft":42.5,"timeleft":"00:01:00"}""",
        )
        assertEquals(QueueStatus.UNKNOWN, decoded.status)
        assertEquals(DownloadProtocol.UNKNOWN, decoded.protocol)
        assertEquals("not-a-parsed-date", decoded.added)
        assertEquals(42.5, decoded.sizeleft, 0.0)
        assertEquals("00:01:00", decoded.timeleft)
        assertEquals(decoded, ArrJson.decodeFromString<QueueResource>(
            ArrJson.encodeToString(QueueResource.serializer(), decoded)))
    }
}
