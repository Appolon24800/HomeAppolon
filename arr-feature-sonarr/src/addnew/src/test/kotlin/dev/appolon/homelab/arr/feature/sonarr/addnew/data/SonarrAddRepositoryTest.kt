package dev.appolon.homelab.arr.feature.sonarr.addnew.data

import dev.appolon.homelab.arr.api.sonarr.FakeSonarrCalendar
import dev.appolon.homelab.arr.api.sonarr.FakeSonarrDiscovery
import dev.appolon.homelab.arr.api.sonarr.FakeSonarrLibrary
import dev.appolon.homelab.arr.api.sonarr.FakeSonarrSystem
import dev.appolon.homelab.arr.api.sonarr.model.ImportListExclusionResource
import dev.appolon.homelab.arr.api.sonarr.model.ImportListResource
import dev.appolon.homelab.arr.api.sonarr.model.MonitorTypes
import dev.appolon.homelab.arr.api.sonarr.model.RootFolderResource
import dev.appolon.homelab.arr.api.sonarr.model.SeasonResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.addnew.SonarrSeriesLookupRunner
import dev.appolon.homelab.arr.feature.sonarr.library.FakeActionQueue
import dev.appolon.homelab.arr.feature.sonarr.library.FakeEntityStore
import dev.appolon.homelab.arr.feature.sonarr.library.data.WriteOutcome
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * Covers the add repository's cache-first lookups, the add-series write paths
 * including offline queueing with an optimistic pending row, import list
 * sync, and exclusion management.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SonarrAddRepositoryTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val serverId = ServerId("server-1")
    private val library = FakeSonarrLibrary()
    private val system = FakeSonarrSystem()
    private val calendar = FakeSonarrCalendar()
    private val discovery = FakeSonarrDiscovery()
    private val store = FakeEntityStore()
    private val queue = FakeActionQueue()

    private var lookupResult: ArrResult<List<SeriesResource>> =
        ArrResult.ok(emptyList())

    private val repository = SonarrAddRepository(
        serverId = serverId,
        store = store,
        seriesApi = library.series,
        lookupRunner = SonarrSeriesLookupRunner { lookupResult },
        rootFolderApi = { system.rootFolderApi.list() },
        qualityProfileApi = { calendar.qualityProfile.list() },
        languageProfileApi = { calendar.languageProfile.list() },
        tagApi = { system.tagApi.list() },
        importListApi = discovery.importList,
        importListExclusionApi = discovery.importListExclusion,
        actionQueue = queue,
        ioDispatcher = dispatcher,
    )

    @Before
    fun setUp() {
        system.rootFolderStore[1] = RootFolderResource(
            id = 1,
            path = "/tv",
            freeSpace = 500L * 1024 * 1024 * 1024,
        )
    }

    private fun lookupSeries(tvdbId: Int, title: String, seasons: List<Int> = listOf(1)) =
        SeriesResource(
            title = title,
            tvdbId = tvdbId,
            year = 2020,
            seasons = seasons.map { SeasonResource(seasonNumber = it) },
        )

    @Test
    fun `lookup passes the term through untouched`() = runTest {
        var seenTerm: String? = null
        lookupResult = ArrResult.ok(listOf(lookupSeries(10, "Aqua")))
        val scoped = SonarrAddRepository(
            serverId = serverId,
            store = store,
            seriesApi = library.series,
            lookupRunner = SonarrSeriesLookupRunner { term ->
                seenTerm = term
                lookupResult
            },
            rootFolderApi = { emptyList() },
            qualityProfileApi = { emptyList() },
            languageProfileApi = { emptyList() },
            tagApi = { emptyList() },
            importListApi = discovery.importList,
            importListExclusionApi = discovery.importListExclusion,
            actionQueue = queue,
            ioDispatcher = dispatcher,
        )
        val result = scoped.lookup("Aqua Teen")
        assertTrue(result is ArrResult.Ok)
        assertEquals("Aqua Teen", seenTerm)
    }

    @Test
    fun `root folders are cached within the ttl`() = runTest {
        val first = repository.rootFolders()
        assertEquals(listOf("/tv"), (first as ArrResult.Ok).value.map { it.path })
        // Fail every later network call; the cache still serves the folders.
        system.error = { _: String -> IOException("down") }
        val second = repository.rootFolders()
        assertEquals(first.value, (second as ArrResult.Ok).value)
    }

    @Test
    fun `add series writes through on success`() = runTest {
        val body = lookupSeries(10, "Aqua")
        val outcome = repository.addSeries(body)
        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Synced)
        val stored = library.seriesStore.values.single()
        assertEquals("Aqua", stored.title)
        // The real id keys the cache row, so the library grid shows it at once.
        val rowKey = stored.id.toString()
        assertNotNull(store.get(serverId, SERIES_TYPE, rowKey, SeriesResource.serializer()))
        assertTrue(queue.enqueued.isEmpty())
    }

    @Test
    fun `add series queues optimistically when offline`() = runTest {
        library.error = { op: String -> if (op == "Series.create") IOException("down") else null }
        val body = lookupSeries(10, "Aqua")
        val outcome = repository.addSeries(body)
        val queued = (outcome as ArrResult.Ok).value as WriteOutcome.Queued
        assertEquals("POST", queue.enqueued.single().method)
        assertEquals("api/v3/series", queue.enqueued.single().path)
        assertTrue(queue.enqueued.single().bodyJson!!.contains("\"tvdbId\":10"))
        assertEquals(queued.actionId, queue.enqueued.single().actionId)
        // Optimistic pending row under the synthetic negative id.
        val pending = store.get(serverId, SERIES_TYPE, "-10", SeriesResource.serializer())
        assertEquals(-10, pending?.id)
        // The queued tvdb id surfaces for the queued badge.
        assertEquals(setOf(10), repository.queuedTvdbIdsAsSet())
    }

    @Test
    fun `add series surfaces auth errors without queueing`() = runTest {
        library.error = { op: String ->
            if (op == "Series.create") {
                HttpException(Response.error<Any>(401, "".toResponseBody()))
            } else {
                null
            }
        }
        val outcome = repository.addSeries(lookupSeries(10, "Aqua"))
        assertTrue(outcome is ArrResult.Err)
        assertTrue((outcome as ArrResult.Err).error is ArrError.Auth)
        assertTrue(queue.enqueued.isEmpty())
        assertEquals(0, store.getAll(serverId, SERIES_TYPE, SeriesResource.serializer()).size)
    }

    @Test
    fun `refresh series reconciles pending rows away`() = runTest {
        store.put(
            serverId,
            SERIES_TYPE,
            "-10",
            lookupSeries(10, "Aqua").copy(id = -10),
            SeriesResource.serializer(),
        )
        library.seriesStore[7] = lookupSeries(10, "Aqua").copy(id = 7)
        assertTrue(repository.refreshSeries() is ArrResult.Ok)
        val cached = store.getAll(serverId, SERIES_TYPE, SeriesResource.serializer())
        assertEquals(listOf(7), cached.map { it.id })
    }

    @Test
    fun `sync import list posts the sync action`() = runTest {
        discovery.importListStore[3] = ImportListResource(id = 3, name = "Trakt")
        val list = discovery.importListStore.values.single()
        val outcome = repository.syncImportList(list)
        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals(listOf(list), discovery.actionCalls["sync"])
    }

    @Test
    fun `sync import list queues when offline`() = runTest {
        val list = ImportListResource(id = 3, name = "Trakt")
        discovery.error = { op: String ->
            if (op == "ImportList.action") IOException("down") else null
        }
        val outcome = repository.syncImportList(list)
        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Queued)
        val action = queue.enqueued.single()
        assertEquals("POST", action.method)
        assertEquals("api/v3/importlist/action/sync", action.path)
    }

    @Test
    fun `add exclusion writes through on success`() = runTest {
        val outcome = repository.addExclusion(10, "Aqua")
        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Synced)
        val stored = discovery.importListExclusionStore.values.single()
        assertEquals(10, stored.tvdbId)
        assertEquals("Aqua", stored.title)
    }

    @Test
    fun `add exclusion queues optimistically when offline`() = runTest {
        discovery.error = { op: String ->
            if (op == "ImportListExclusion.create") IOException("down") else null
        }
        val outcome = repository.addExclusion(10, "Aqua")
        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Queued)
        val action = queue.enqueued.single()
        assertEquals("api/v3/importlistexclusion", action.path)
        val pending = store.get(
            serverId,
            EXCLUSION_TYPE,
            "-10",
            ImportListExclusionResource.serializer(),
        )
        assertEquals(-10, pending?.id)
        assertEquals(setOf(10), repository.queuedTvdbIdsAsSet())
    }

    @Test
    fun `delete exclusion writes through on success`() = runTest {
        discovery.importListExclusionStore[5] =
            ImportListExclusionResource(id = 5, tvdbId = 10, title = "Aqua")
        store.put(
            serverId,
            EXCLUSION_TYPE,
            "5",
            discovery.importListExclusionStore.values.single(),
            ImportListExclusionResource.serializer(),
        )
        val outcome = repository.deleteExclusion(discovery.importListExclusionStore.values.single())
        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Synced)
        assertTrue(discovery.importListExclusionStore.isEmpty())
        assertTrue(
            store.getAll(serverId, EXCLUSION_TYPE, ImportListExclusionResource.serializer())
                .isEmpty(),
        )
    }

    @Test
    fun `delete exclusion queues when offline`() = runTest {
        discovery.importListExclusionStore[5] =
            ImportListExclusionResource(id = 5, tvdbId = 10, title = "Aqua")
        discovery.error = { op: String ->
            if (op == "ImportListExclusion.delete") IOException("down") else null
        }
        val outcome = repository.deleteExclusion(discovery.importListExclusionStore.values.single())
        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Queued)
        val action = queue.enqueued.single()
        assertEquals("DELETE", action.method)
        assertEquals("api/v3/importlistexclusion/5", action.path)
    }

    @Test
    fun `deleting a pending exclusion cancels its queued create`() = runTest {
        discovery.error = { op: String ->
            if (op == "ImportListExclusion.create") IOException("down") else null
        }
        repository.addExclusion(10, "Aqua")
        assertEquals(1, queue.enqueued.size)
        val pending = store.getAll(
            serverId,
            EXCLUSION_TYPE,
            ImportListExclusionResource.serializer(),
        ).single()
        val outcome = repository.deleteExclusion(pending)
        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Synced)
        assertTrue(queue.enqueued.isEmpty())
        assertTrue(
            store.getAll(serverId, EXCLUSION_TYPE, ImportListExclusionResource.serializer())
                .isEmpty(),
        )
    }

    @Test
    fun `default monitored seasons follow the strategy`() {
        val seasons = listOf(0, 1, 2, 3)
        assertEquals(
            setOf(0, 1, 2, 3),
            SeasonMonitoring.defaultMonitored(MonitorTypes.ALL, seasons),
        )
        assertEquals(
            setOf(1),
            SeasonMonitoring.defaultMonitored(MonitorTypes.FIRSTSEASON, seasons),
        )
        assertEquals(
            setOf(3),
            SeasonMonitoring.defaultMonitored(MonitorTypes.LATESTSEASON, seasons),
        )
        assertEquals(
            setOf(1, 2, 3),
            SeasonMonitoring.defaultMonitored(MonitorTypes.UNMONITORSPECIALS, seasons),
        )
        assertEquals(
            emptySet<Int>(),
            SeasonMonitoring.defaultMonitored(MonitorTypes.NONE, seasons),
        )
        assertEquals(
            emptySet<Int>(),
            SeasonMonitoring.defaultMonitored(MonitorTypes.FIRSTSEASON, listOf(0)),
        )
    }

    private suspend fun SonarrAddRepository.queuedTvdbIdsAsSet(): Set<Int> =
        queuedTvdbIds().first()

    private companion object {
        val SERIES_TYPE = EntityType("sonarr.series")
        val EXCLUSION_TYPE = EntityType("sonarr.importlistexclusion")
    }
}
