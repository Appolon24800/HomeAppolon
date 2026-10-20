package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.BlocklistBulkResource
import dev.appolon.homelab.arr.api.lidarr.model.CommandResource
import dev.appolon.homelab.arr.api.lidarr.model.CommandStatus
import dev.appolon.homelab.arr.api.lidarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.lidarr.model.EntityHistoryEventType
import dev.appolon.homelab.arr.api.lidarr.model.HistoryResource
import dev.appolon.homelab.arr.api.lidarr.model.ManualImportResource
import dev.appolon.homelab.arr.api.lidarr.model.ManualImportUpdateResource
import dev.appolon.homelab.arr.api.lidarr.model.ParseResource
import dev.appolon.homelab.arr.api.lidarr.model.QueueBulkResource
import dev.appolon.homelab.arr.api.lidarr.model.QueueResource
import dev.appolon.homelab.arr.api.lidarr.model.QueueResourcePagingResource
import dev.appolon.homelab.arr.api.lidarr.model.ReleaseResource
import dev.appolon.homelab.arr.api.lidarr.model.SortDirection
import dev.appolon.homelab.arr.api.lidarr.model.TrackResource
import dev.appolon.homelab.arr.api.lidarr.model.TrackedDownloadStatus
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException

class FakeLidarrQueueTest {
    private fun seeded(): FakeLidarrQueue = FakeLidarrQueue(now = { "2026-01-01T00:00:00Z" })
        .apply {
            val resource = queueDto<QueueResourcePagingResource>("queue/getQueue").records!!.single()
            queue[1] = resource
            queue[2] = resource.copy(id = 2, title = "a", artistId = 4, albumId = 5)
            queue[3] = resource.copy(id = 3, artistId = null, albumId = null,
                trackedDownloadStatus = TrackedDownloadStatus.ERROR)
        }

    @Test
    fun queueFiltersSortsPagesAndIncludes() = runTest {
        val fake = seeded()
        val result = fake.getQueue(
            page = 1, pageSize = 1, sortKey = "title", sortDirection = SortDirection.ASCENDING,
        )
        assertEquals(2, result.totalRecords)
        assertEquals(2, result.records!!.single().id)
        assertNull(result.records.single().artist)
        assertNull(result.records.single().album)
        assertEquals(1, fake.getQueue(
            artistIds = listOf(2), protocol = DownloadProtocol.TORRENT, quality = listOf(1),
            includeArtist = true, includeAlbum = true,
        ).records!!.single().id)
        assertEquals(0, fake.getQueue(protocol = DownloadProtocol.USENET).totalRecords)
        assertEquals(3, fake.getQueue(includeUnknownArtistItems = true).totalRecords)
        assertTrue(fake.getQueue(page = Int.MAX_VALUE, pageSize = Int.MAX_VALUE)
            .records!!.isEmpty())
        assertEquals(2, fake.getQueueDetails(artistId = 4, albumIds = listOf(5))
            .single().id)
        assertNull(fake.getQueueDetails(artistId = 2).single().artist)
        assertEquals(3, fake.getQueueDetails(artistId = 2).single().album!!.id)
    }

    @Test
    fun queueActionsAndDeletesUpdateState() = runTest {
        val fake = seeded()
        fake.grabQueue(1)
        fake.grabQueueBulk(QueueBulkResource(listOf(2, 3)))
        assertEquals(listOf(1, 2, 3), fake.grabbedQueueIds)
        assertTrue(fake.queue.values.all { it.downloadForced })
        val status = fake.getQueueStatus()
        assertEquals(3, status.totalCount)
        assertEquals(2, status.count)
        assertEquals(1, status.unknownCount)
        assertTrue(status.warnings)
        assertTrue(status.unknownErrors)
        fake.deleteQueue(1, removeFromClient = false, blocklist = true,
            skipRedownload = true, changeCategory = true)
        assertFalse(1 in fake.queue)
        assertFalse(1 in fake.removedFromClient)
        assertTrue(1 in fake.skippedRedownload)
        assertTrue(1 in fake.changedCategory)
        assertEquals(2, fake.blocklist.values.single().artistId)
        fake.deleteQueueBulk(QueueBulkResource(listOf(2, 3)))
        assertTrue(fake.queue.isEmpty())
        assertEquals(setOf(2, 3), fake.removedFromClient)
        val blocked = fake.getBlocklist(sortKey = "date", sortDirection = SortDirection.DESCENDING)
        assertEquals("2026-01-01T00:00:00Z", blocked.records!!.single().date)
        fake.deleteBlocklist(blocked.records.single().id)
        fake.queue[1] = QueueResource(id = 1)
        fake.queue[2] = QueueResource(id = 2)
        fake.deleteQueueBulk(QueueBulkResource(listOf(1, 2)), blocklist = true)
        fake.deleteBlocklistBulk(BlocklistBulkResource(fake.blocklist.keys.toList()))
        assertTrue(fake.blocklist.isEmpty())
    }

    @Test
    fun historyFiltersIncludesAndFailedActions() = runTest {
        val fake = seeded()
        val item = HistoryResource(
            id = 1, artistId = 2, albumId = 3, trackId = 4, date = "2026-01-01T00:00:00Z",
            downloadId = "download", eventType = EntityHistoryEventType.GRABBED,
            artist = ArtistResource(id = 2), album = AlbumResource(id = 3),
            track = TrackResource(id = 4), quality = fake.queue.getValue(1).quality,
        )
        fake.history[1] = item
        fake.history[2] = item.copy(id = 2, artistId = 5, date = "2026-01-02T00:00:00Z")
        fake.historyEventTypeIds[EntityHistoryEventType.GRABBED] = 1
        val result = fake.getHistory(
            artistIds = listOf(2), albumId = 3, eventType = listOf(1),
            downloadId = "download", quality = listOf(1), includeTrack = true,
        )
        assertEquals(1, result.totalRecords)
        assertNull(result.records!!.single().artist)
        assertEquals(4, result.records.single().track!!.id)
        assertEquals(listOf(2), fake.getHistorySince(date = "2026-01-01T01:00:00+01:00")
            .map { it.id })
        assertEquals(listOf(1), fake.getArtistHistory(2, 3, EntityHistoryEventType.GRABBED)
            .map { it.id })
        fake.failHistory(1)
        assertEquals(EntityHistoryEventType.DOWNLOAD_FAILED, fake.history.getValue(1).eventType)
        assertEquals(setOf(1), fake.failedHistoryIds)
    }

    @Test
    fun commandsAreStoredAndCancelled() = runTest {
        val fake = seeded()
        val command = fake.addCommand(CommandResource(name = "RefreshArtist"))
        assertEquals("2026-01-01T00:00:00Z", command.queued)
        assertEquals(command, fake.getCommand(command.id))
        assertEquals(listOf(command), fake.getCommands())
        fake.deleteCommand(command.id)
        assertEquals(CommandStatus.CANCELLED, fake.getCommand(command.id).status)
    }

    @Test
    fun manualImportFiltersAndAppliesUpdates() = runTest {
        val fake = seeded()
        fake.manualImports[1] = ManualImportResource(
            id = 1, path = "/incoming/album/track.flac", artist = ArtistResource(id = 2),
            album = AlbumResource(id = 3), tracks = listOf(TrackResource(id = 4, hasFile = true)),
            downloadId = "download",
        )
        assertTrue(fake.getManualImport(folder = "/incoming").isEmpty())
        assertEquals(1, fake.getManualImport(
            folder = "/incoming", downloadId = "download", artistId = 2,
            filterExistingFiles = false, replaceExistingFiles = false,
        ).single().id)
        assertTrue(fake.getManualImport(folder = "/incom", filterExistingFiles = false).isEmpty())
        val update = ManualImportUpdateResource(
            id = 1, artistId = 5, albumId = 6, albumReleaseId = 7, trackIds = listOf(4),
            releaseGroup = "string", replaceExistingFiles = true,
        )
        fake.updateManualImport(listOf(update))
        val item = fake.manualImports.getValue(1)
        assertEquals(5, item.artist!!.id)
        assertEquals(6, item.album!!.id)
        assertEquals(7, item.albumReleaseId)
        assertEquals(listOf(update), fake.manualImportUpdates)
    }

    @Test
    fun releasesParseAndPingUseSeededData() = runTest {
        val fake = seeded()
        val release = ReleaseResource(artistId = 2, albumId = 3, title = "string")
        val pushed = fake.pushRelease(release)
        assertEquals(pushed, fake.getReleases(3, 2).single())
        assertTrue(fake.getReleases(4, 2).isEmpty())
        assertEquals(pushed, fake.grabRelease(pushed))
        assertEquals(listOf(pushed), fake.grabbedReleases)
        assertEquals(listOf(pushed), fake.pushedReleases)
        fake.parsedTitles["title"] = ParseResource(id = 1, title = "title")
        assertEquals(1, fake.parse("title").id)
        assertEquals("unknown", fake.parse("unknown").title)
        assertEquals("OK", fake.ping().status)
        assertEquals(200, fake.headPing().code())
        fake.headPingStatus = 503
        assertEquals(503, fake.headPing().code())
    }

    @Test
    fun allOperationsApplyLatencyThenErrorBeforeStateChanges() = runTest {
        val order = mutableListOf<String>()
        val failure = IOException("injected")
        val fake = seeded().apply {
            latency = { operation -> delay(10); order.add("latency:$operation") }
            error = { operation -> order.add("error:$operation"); failure }
        }
        val calls: List<suspend () -> Any?> = listOf(
            { fake.getQueue() }, { fake.deleteQueue(1) },
            { fake.deleteQueueBulk(QueueBulkResource(listOf(1))) },
            { fake.grabQueue(1) }, { fake.grabQueueBulk(QueueBulkResource(listOf(1))) },
            { fake.getQueueDetails() }, { fake.getQueueStatus() },
            { fake.getBlocklist() }, { fake.deleteBlocklist(1) },
            { fake.deleteBlocklistBulk(BlocklistBulkResource(listOf(1))) },
            { fake.getHistory() }, { fake.getHistorySince() }, { fake.getArtistHistory() },
            { fake.failHistory(1) }, { fake.getCommand(1) }, { fake.deleteCommand(1) },
            { fake.addCommand(CommandResource()) }, { fake.getCommands() },
            { fake.getManualImport() }, { fake.updateManualImport(emptyList()) },
            { fake.getReleases() }, { fake.grabRelease(ReleaseResource()) },
            { fake.pushRelease(ReleaseResource()) }, { fake.parse() },
            { fake.ping() }, { fake.headPing() },
        )
        calls.forEach { call ->
            try {
                call()
                fail("Expected injected failure")
            } catch (caught: IOException) {
                assertSame(failure, caught)
            }
        }
        assertEquals(52, order.size)
        order.chunked(2).forEach { (latency, error) ->
            assertEquals(latency.removePrefix("latency:"), error.removePrefix("error:"))
        }
        assertEquals(26, order.filter { it.startsWith("latency:") }.toSet().size)
        assertEquals(3, fake.queue.size)
        assertTrue(fake.blocklist.isEmpty())
        assertTrue(fake.commands.isEmpty())
        assertTrue(fake.grabbedQueueIds.isEmpty())
        assertTrue(fake.grabbedReleases.isEmpty())
        assertTrue(fake.pushedReleases.isEmpty())
    }

    @Test
    fun missingIdsProduce404AndBulkOperationsDoNotPartiallyMutate() = runTest {
        val fake = seeded()
        val calls: List<suspend () -> Any?> = listOf(
            { fake.deleteQueue(999) }, { fake.grabQueue(999) },
            { fake.deleteQueueBulk(QueueBulkResource(listOf(1, 999))) },
            { fake.grabQueueBulk(QueueBulkResource(listOf(1, 999))) },
            { fake.deleteBlocklist(999) },
            { fake.deleteBlocklistBulk(BlocklistBulkResource(listOf(999))) },
            { fake.failHistory(999) }, { fake.getCommand(999) },
            { fake.deleteCommand(999) },
            { fake.updateManualImport(listOf(ManualImportUpdateResource(id = 999))) },
        )
        calls.forEach { call ->
            try {
                call()
                fail("Expected missing resource")
            } catch (error: HttpException) {
                assertEquals(404, error.code())
            }
        }
        assertEquals(3, fake.queue.size)
        assertTrue(fake.grabbedQueueIds.isEmpty())
        assertTrue(fake.queue.values.none { it.downloadForced })
    }
}
