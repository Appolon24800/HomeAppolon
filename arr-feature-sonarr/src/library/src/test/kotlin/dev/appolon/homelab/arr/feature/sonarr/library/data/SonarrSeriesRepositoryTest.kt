package dev.appolon.homelab.arr.feature.sonarr.library.data

import dev.appolon.homelab.arr.api.sonarr.FakeSonarrLibrary
import dev.appolon.homelab.arr.api.sonarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.SeasonPassResource
import dev.appolon.homelab.arr.api.sonarr.model.SeasonPassSeriesResource
import dev.appolon.homelab.arr.api.sonarr.model.SeasonResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesEditorResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.api.sonarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.library.FakeActionQueue
import dev.appolon.homelab.arr.feature.sonarr.library.FakeEntityStore
import dev.appolon.homelab.arr.net.ArrJson
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the repository contract: cache-first TTL reads, writes through the
 * editor and season pass endpoints, offline enqueue with optimistic state,
 * and command dispatch bodies.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SonarrSeriesRepositoryTest {

    private val serverId = ServerId("server-1")
    private val fake = FakeSonarrLibrary()
    private val store = FakeEntityStore()
    private val queue = FakeActionQueue()
    private val commands = mutableListOf<Pair<String, Int?>>()
    private var commandError: Throwable? = null
    private var clock = 1_000L

    private val repository = SonarrSeriesRepository(
        serverId = serverId,
        store = store,
        seriesApi = fake.series,
        seriesEditorApi = fake.seriesEditor,
        seasonPassApi = fake.seasonPass,
        qualityProfileApi = {
            listOf(QualityProfileResource(id = 1, name = "HD"), QualityProfileResource(id = 2, name = "UHD"))
        },
        tagApi = { listOf(TagResource(id = 10, label = "kids")) },
        commandRunner = { command, seriesId ->
            commands += command to seriesId
            commandError?.let { throw it } ?: ArrResult.ok(Unit)
        },
        actionQueue = queue,
        now = { clock },
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    private fun series(id: Int, monitored: Boolean = true, tags: Set<Int> = emptySet()) =
        SeriesResource(
            id = id,
            title = "S$id",
            monitored = monitored,
            tags = tags.ifEmpty { null },
            seasons = listOf(SeasonResource(seasonNumber = 1, monitored = true)),
            qualityProfileId = 1,
        )

    private fun seeded(vararg ids: Int) {
        ids.forEach { fake.seriesStore[it] = series(it) }
    }

    @Test
    fun `refresh caches the fetched library`() = runTest {
        seeded(1, 2)
        val result = repository.refresh()
        assertTrue(result is ArrResult.Ok)
        assertEquals(listOf(1, 2), repository.cachedSeries().map { it.id })
    }

    @Test
    fun `repeated offline edits keep the same action id`() = runTest {
        seeded(1)
        repository.refresh()
        fake.error = { IOException("offline") }
        val editor = SeriesEditorResource(seriesIds = listOf(1), monitored = false)
        val first = repository.editSeries(editor)
        clock += 1_000
        val second = repository.editSeries(editor)
        val firstId = ((first as ArrResult.Ok).value as WriteOutcome.Queued).actionId
        val secondId = ((second as ArrResult.Ok).value as WriteOutcome.Queued).actionId
        assertEquals(firstId, secondId)
        assertEquals(false, repository.cachedSeries().first().monitored)
    }

    @Test
    fun `refresh reconciles removed series`() = runTest {
        seeded(1, 2)
        repository.refresh()
        fake.seriesStore.remove(2)
        repository.refresh()
        assertEquals(listOf(1), repository.cachedSeries().map { it.id })
    }

    @Test
    fun `isStale honors the ttl`() = runTest {
        seeded(1)
        repository.refresh()
        clock = 2_000L
        assertTrue(!repository.isStale(ttlMillis = 5_000))
        assertTrue(repository.isStale(ttlMillis = 500))
    }

    @Test
    fun `editor write succeeds and merges locally`() = runTest {
        seeded(1)
        repository.refresh()
        val editor = SeriesEditorResource(seriesIds = listOf(1), monitored = false)
        val outcome = repository.editSeries(editor)
        assertTrue((outcome as ArrResult.Ok).value == WriteOutcome.Synced)
        assertEquals(false, repository.cachedSeries().single().monitored)
        assertTrue(queue.enqueued.isEmpty())
    }

    @Test
    fun `offline editor write queues with put body and applies optimistically`() = runTest {
        seeded(1)
        repository.refresh()
        fake.error = { if (it == "SeriesEditor.update") IOException("offline") else null }
        val editor = SeriesEditorResource(seriesIds = listOf(1), monitored = false)
        val outcome = repository.editSeries(editor)
        assertTrue(outcome is ArrResult.Ok && outcome.value is WriteOutcome.Queued)
        val action = queue.enqueued.single()
        assertEquals("PUT", action.method)
        assertEquals("api/v3/series/editor", action.path)
        val root = ArrJson.parseToJsonElement(action.bodyJson!!).jsonObject
        val ids = (root["seriesIds"] as kotlinx.serialization.json.JsonArray)
            .map { it.jsonPrimitive.int }
        assertEquals(listOf(1), ids)
        assertEquals(false, repository.cachedSeries().single().monitored)
        assertEquals(setOf(1), repository.queuedSeriesIds().first())
    }

    @Test
    fun `offline delete queues and removes locally`() = runTest {
        seeded(1, 2)
        repository.refresh()
        fake.error = { if (it == "SeriesEditor.delete") IOException("offline") else null }
        val outcome = repository.deleteSeries(listOf(1), deleteFiles = true, addImportListExclusion = false)
        assertTrue(outcome is ArrResult.Ok && outcome.value is WriteOutcome.Queued)
        assertEquals("DELETE", queue.enqueued.single().method)
        assertEquals(listOf(2), repository.cachedSeries().map { it.id })
    }

    @Test
    fun `delete succeeds and drops rows`() = runTest {
        seeded(1, 2)
        repository.refresh()
        val outcome = repository.deleteSeries(listOf(2), deleteFiles = false, addImportListExclusion = false)
        assertTrue((outcome as ArrResult.Ok).value == WriteOutcome.Synced)
        assertEquals(listOf(1), repository.cachedSeries().map { it.id })
    }

    @Test
    fun `season pass succeeds and applies season flags locally`() = runTest {
        seeded(1)
        repository.refresh()
        val pass = SeasonPassResource(
            series = listOf(
                SeasonPassSeriesResource(
                    id = 1,
                    monitored = false,
                    seasons = listOf(SeasonResource(seasonNumber = 1, monitored = false)),
                ),
            ),
        )
        val outcome = repository.seasonPass(pass)
        assertTrue((outcome as ArrResult.Ok).value == WriteOutcome.Synced)
        val cached = repository.cachedSeries().single()
        assertEquals(false, cached.monitored)
        assertEquals(false, cached.seasons!!.single().monitored)
    }

    @Test
    fun `offline season pass queues with post body`() = runTest {
        seeded(1)
        repository.refresh()
        fake.error = { if (it == "SeasonPass.create") IOException("offline") else null }
        val pass = SeasonPassResource(
            series = listOf(SeasonPassSeriesResource(id = 1, monitored = false)),
        )
        val outcome = repository.seasonPass(pass)
        assertTrue(outcome is ArrResult.Ok && outcome.value is WriteOutcome.Queued)
        val action = queue.enqueued.single()
        assertEquals("POST", action.method)
        assertEquals("api/v3/seasonpass", action.path)
        assertEquals(setOf(1), repository.queuedSeriesIds().first())
    }

    @Test
    fun `command for a selection sends one command per series`() = runTest {
        seeded(1, 2)
        repository.refresh()
        val outcome = repository.runCommand(SeriesCommand.REFRESH_METADATA, listOf(1, 2))
        assertTrue((outcome as ArrResult.Ok).value == WriteOutcome.Synced)
        assertEquals(
            listOf("RefreshSeries" to 1, "RefreshSeries" to 2),
            commands,
        )
    }

    @Test
    fun `library wide command sends one unscoped command`() = runTest {
        seeded(1)
        repository.refresh()
        repository.runCommand(SeriesCommand.SCAN_DISK, null)
        assertEquals(listOf("RescanSeries" to null), commands)
    }

    @Test
    fun `offline command queues replayable bodies`() = runTest {
        seeded(1)
        repository.refresh()
        commandError = IOException("offline")
        val outcome = repository.runCommand(SeriesCommand.REFRESH_METADATA, listOf(1))
        assertTrue(outcome is ArrResult.Ok && outcome.value is WriteOutcome.Queued)
        val action = queue.enqueued.single()
        assertEquals("api/v3/command", action.path)
        val root = ArrJson.parseToJsonElement(action.bodyJson!!).jsonObject
        assertEquals("RefreshSeries", root["name"]!!.jsonPrimitive.content)
        assertEquals(1, root["seriesId"]!!.jsonPrimitive.content.toInt())
        assertEquals(setOf(1), repository.queuedSeriesIds().first())
    }

    @Test
    fun `queuedCount counts only this server`() = runTest {
        seeded(1)
        repository.refresh()
        commandError = IOException("offline")
        repository.runCommand(SeriesCommand.SCAN_DISK, listOf(1))
        assertEquals(1, repository.queuedCount().first())
    }

    @Test
    fun `lookups cache with their own ttl`() = runTest {
        val profiles = repository.qualityProfiles()
        assertTrue((profiles as ArrResult.Ok).value.map { it.name }.contains("UHD"))
        val tags = repository.tags()
        assertTrue((tags as ArrResult.Ok).value.single().label == "kids")
    }
}
