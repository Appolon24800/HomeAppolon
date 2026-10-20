package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.BlocklistBulkResource
import dev.appolon.homelab.arr.api.sonarr.model.BlocklistResource
import dev.appolon.homelab.arr.api.sonarr.model.CommandResource
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeHistoryEventType
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.api.sonarr.model.HistoryResource
import dev.appolon.homelab.arr.api.sonarr.model.ManualImportReprocessResource
import dev.appolon.homelab.arr.api.sonarr.model.ManualImportResource
import dev.appolon.homelab.arr.api.sonarr.model.ParseResource
import dev.appolon.homelab.arr.api.sonarr.model.PingResource
import dev.appolon.homelab.arr.api.sonarr.model.QueueBulkResource
import dev.appolon.homelab.arr.api.sonarr.model.QueueResource
import dev.appolon.homelab.arr.api.sonarr.model.QueueStatus
import dev.appolon.homelab.arr.api.sonarr.model.ReleaseResource
import dev.appolon.homelab.arr.api.sonarr.model.SortDirection
import dev.appolon.homelab.arr.net.ArrJson
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FakeSonarrQueueTest {
    @Test
    fun queueFiltersPagesSortsGrabsAndDeletes() = runTest {
        val fake = FakeSonarrQueue()
        fake.queueStore[1] = QueueResource(id = 1, seriesId = 5, episodeId = 9, size = 20.0)
        fake.queueStore[2] = QueueResource(id = 2, seriesId = 5, episodeId = 8, size = 10.0)
        fake.queueStore[3] = QueueResource(id = 3)
        val page = fake.queue.list(
            page = 2, pageSize = 1, seriesIds = listOf(5),
            sortKey = "size", sortDirection = SortDirection.ASCENDING
        )
        assertEquals(2, page.totalRecords)
        assertEquals(listOf(1), page.records!!.map { it.id })
        assertEquals(listOf(1), fake.queueDetails.list(episodeIds = listOf(9)).map { it.id })
        assertEquals(1, fake.queueStatus.list().unknownCount)
        fake.queueAction.create(1)
        fake.queueAction.createBulk(QueueBulkResource(ids = listOf(2)))
        assertEquals(QueueStatus.DOWNLOADING, fake.queueStore[1]!!.status)
        assertEquals(listOf(1, 2), fake.grabbedQueueIds)
        fake.queue.delete(1)
        fake.queue.deleteBulk(body = QueueBulkResource(ids = listOf(2, 3)))
        assertTrue(fake.queueStore.isEmpty())
    }

    @Test
    fun blocklistAndHistoryMutations() = runTest {
        val fake = FakeSonarrQueue()
        fake.blocklistStore[1] = BlocklistResource(id = 1, seriesId = 5)
        fake.blocklistStore[2] = BlocklistResource(id = 2, seriesId = 6)
        assertEquals(1, fake.blocklist.list(seriesIds = listOf(5)).totalRecords)
        fake.blocklist.delete(1)
        fake.blocklist.deleteBulk(BlocklistBulkResource(ids = listOf(2)))
        assertTrue(fake.blocklistStore.isEmpty())
        fake.historyStore[1] = HistoryResource(
            id = 1, seriesId = 5, episodeId = 9, date = "2026-01-01T00:00:00Z",
            episode = EpisodeResource(seasonNumber = 2)
        )
        assertEquals(1, fake.history.list(episodeId = 9).totalRecords)
        assertEquals(1, fake.history.series(seriesId = 5, seasonNumber = 2).size)
        assertTrue(fake.history.since(date = "2026-01-02T00:00:00Z").isEmpty())
        fake.history.markFailed(1)
        assertEquals(
            EpisodeHistoryEventType.DOWNLOAD_FAILED, fake.historyStore[1]!!.eventType
        )
    }

    @Test
    fun importsCommandsReleasesParseAndPingKeepState() = runTest {
        val fake = FakeSonarrQueue()
        fake.manualImports.add(ManualImportResource(id = 4, path = "/downloads/file.mkv"))
        assertEquals(1, fake.manualImport.list(folder = "/downloads").size)
        fake.manualImport.create(listOf(ManualImportReprocessResource(id = 4, seasonNumber = 2)))
        assertEquals(2, fake.manualImports.single().seasonNumber)
        assertEquals(1, fake.reprocessedImports.size)
        val command = fake.command.create(CommandResource(name = "RefreshSeries"))
        assertEquals(command, fake.command.get(command.id).body())
        assertEquals(listOf(command), fake.command.list())
        fake.command.delete(command.id)
        assertEquals(404, fake.command.get(command.id).code())
        val release = ReleaseResource(id = 3, seriesId = 5, title = "string")
        assertEquals(listOf(release), fake.releasePush.create(release))
        assertEquals(listOf(release), fake.release.list(seriesId = 5))
        fake.release.create(release)
        assertEquals(listOf(release), fake.grabbedReleases)
        fake.parsedTitles["string"] = ParseResource(title = "string", id = 3)
        assertEquals(3, fake.parse.list(title = "string").id)
        assertEquals(PingResource("OK"), fake.ping.list().body())
        fake.pingStatusCode = 500
        assertEquals(500, fake.ping.list().code())
        assertEquals(500, fake.ping.head().code())
    }

    @Test
    fun latencyAndFailuresRunBeforeMutations() = runTest {
        val expected = IOException("offline")
        val visited = mutableListOf<String>()
        val fake = FakeSonarrQueue(
            latency = { visited.add(it); delay(100) },
            error = { expected }
        )
        fake.queueStore[1] = QueueResource(id = 1)
        val calls: List<Pair<String, suspend () -> Unit>> = listOf(
            "Queue.delete" to { fake.queue.delete(1) },
            "QueueAction.create" to { fake.queueAction.create(1) },
            "QueueDetails.list" to { fake.queueDetails.list(); Unit },
            "QueueStatus.list" to { fake.queueStatus.list(); Unit },
            "Blocklist.list" to { fake.blocklist.list(); Unit },
            "History.list" to { fake.history.list(); Unit },
            "ManualImport.list" to { fake.manualImport.list(); Unit },
            "Command.list" to { fake.command.list(); Unit },
            "Release.list" to { fake.release.list(); Unit },
            "ReleasePush.create" to { fake.releasePush.create(ReleaseResource()); Unit },
            "Parse.list" to { fake.parse.list(); Unit },
            "Ping.list" to { fake.ping.list(); Unit }
        )
        for ((operation, call) in calls) {
            try {
                call()
                fail("Expected failure for $operation")
            } catch (error: IOException) {
                assertSame(expected, error)
            }
        }
        assertEquals(calls.map { it.first }, visited)
        assertTrue(fake.queueStore.containsKey(1))
        assertTrue(fake.pushedReleases.isEmpty())
    }

    @Test
    fun unknownEnumsUseDefaultsAndDatesRemainStrings() {
        val value = ArrJson.decodeFromString<QueueResource>(
            """{"status":"futureStatus","added":"2026-01-01T02:03:04+02:00"}"""
        )
        assertEquals(QueueStatus.UNKNOWN, value.status)
        assertEquals("2026-01-01T02:03:04+02:00", value.added)
        assertFalse(value.episodeHasFile)
    }
}
