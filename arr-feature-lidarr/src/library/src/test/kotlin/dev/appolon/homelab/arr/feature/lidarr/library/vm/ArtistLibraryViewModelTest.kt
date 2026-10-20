package dev.appolon.homelab.arr.feature.lidarr.library.vm

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrLibrary
import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.AlbumStatisticsResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.MetadataProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.NewItemMonitorTypes
import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileResource
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.FilterValue
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.library.FakeActionQueue
import dev.appolon.homelab.arr.feature.lidarr.library.FakeEntityStore
import dev.appolon.homelab.arr.feature.lidarr.library.data.ArtistCommandRunner
import dev.appolon.homelab.arr.feature.lidarr.library.data.ArtistLibraryFilters
import dev.appolon.homelab.arr.feature.lidarr.library.data.ArtistLibrarySorts
import dev.appolon.homelab.arr.feature.lidarr.library.data.ArtistPresentation
import dev.appolon.homelab.arr.feature.lidarr.library.data.LidarrArtistsRepository
import dev.appolon.homelab.arr.feature.lidarr.library.data.SavedArtistFiltersStore
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArtistLibraryViewModelTest {

    private val main = UnconfinedTestDispatcher()
    private val now: Instant = Instant.parse("2026-10-20T00:00:00Z")

    private lateinit var fake: FakeLidarrLibrary
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeActionQueue
    private lateinit var vm: ArtistLibraryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun build(configure: FakeLidarrLibrary.() -> Unit = {}): ArtistLibraryViewModel {
        fake = FakeLidarrLibrary()
        store = FakeEntityStore()
        queue = FakeActionQueue()
        fake.artists.putAll(
            listOf(
                ArtistResource(
                    id = 1,
                    artistName = "Alpha",
                    sortName = "alpha",
                    monitored = true,
                    monitorNewItems = NewItemMonitorTypes.ALL,
                ),
                ArtistResource(
                    id = 2,
                    artistName = "Beta",
                    sortName = "beta",
                    monitored = true,
                    monitorNewItems = NewItemMonitorTypes.ALL,
                ),
                ArtistResource(
                    id = 3,
                    artistName = "Gamma",
                    sortName = "gamma",
                    monitored = false,
                    monitorNewItems = NewItemMonitorTypes.NEW,
                ),
            ).associateBy { it.id },
        )
        fake.albums.putAll(
            listOf(
                album(11, artistId = 1, monitored = true, trackFileCount = 1, releaseDate = "2020-01-01"),
                album(12, artistId = 1, monitored = true, trackFileCount = 0, releaseDate = "2027-01-01"),
                album(21, artistId = 2, monitored = true, trackFileCount = 1, releaseDate = "2019-05-05"),
            ).associateBy { it.id },
        )
        fake.configure()
        val repository = LidarrArtistsRepository(
            serverId = ServerId("s1"),
            store = store,
            artistApi = fake,
            artistEditorApi = fake,
            albumApi = fake,
            albumStudioApi = fake,
            qualityProfileApi = { listOf(QualityProfileResource(id = 1, name = "Lossless")) },
            metadataProfileApi = { listOf(MetadataProfileResource(id = 1, name = "Standard")) },
            commandRunner = ArtistCommandRunner { _, _ -> ArrResult.ok(Unit) },
            actionQueue = queue,
            // Unconfined keeps the IO hop synchronous so advanceUntilIdle sees writes.
            ioContext = Dispatchers.Unconfined,
        )
        return ArtistLibraryViewModel(
            repository = repository,
            savedFilters = SavedArtistFiltersStore(store, ServerId("s1")),
            now = { now },
        )
    }

    private fun album(
        id: Int,
        artistId: Int,
        monitored: Boolean,
        trackFileCount: Int,
        releaseDate: String,
    ) = AlbumResource(
        id = id,
        artistId = artistId,
        title = "Album $id",
        monitored = monitored,
        releaseDate = releaseDate,
        statistics = AlbumStatisticsResource(trackFileCount = trackFileCount, trackCount = 10),
    )

    private fun runVm(block: suspend kotlinx.coroutines.test.TestScope.() -> Unit) = runTest {
        vm = build()
        val job = launch { vm.lce.collect {} }
        advanceUntilIdle()
        block()
        advanceUntilIdle()
        job.cancel()
    }

    @Test
    fun `initial load renders content from the network refresh`() = runVm {
        val lce = vm.lce.value
        assertTrue(lce is LceState.Content)
        assertEquals(3, vm.state.value.artists.size)
        assertFalse((lce as LceState.Content).refreshing)
    }

    @Test
    fun `empty library renders the empty state`() = runTest {
        vm = build {
            artists.clear()
            albums.clear()
        }
        val job = launch { vm.lce.collect {} }
        advanceUntilIdle()
        assertTrue(vm.lce.value is LceState.Empty)
        job.cancel()
    }

    @Test
    fun `refresh failure with no cache renders the error state`() = runTest {
        vm = build {
            artists.clear()
            albums.clear()
            error = { _ -> IOException("offline") }
        }
        val job = launch { vm.lce.collect {} }
        advanceUntilIdle()
        assertTrue(vm.lce.value is LceState.Error)
        job.cancel()
    }

    @Test
    fun `album summaries flow into state with release and missing counts`() = runVm {
        val summaries = vm.state.value.summaries
        val alpha = summaries.getValue(1)
        assertEquals(2, alpha.albumCount)
        assertEquals(1, alpha.released)
        assertEquals(1, alpha.upcoming)
        assertEquals(1, alpha.missing)
        // Artists without albums carry no summary entry and count as zero.
        assertEquals(0, summaries[3]?.albumCount ?: 0)
    }

    @Test
    fun `missing filter narrows through the summaries`() = runVm {
        vm.setFilterValue(ArtistLibraryFilters.MISSING, FilterValue.Toggle(true))
        assertEquals(listOf(1), vm.state.value.presentation.visible.map { it.id })
    }

    @Test
    fun `monitored filter narrows and resets the window`() = runVm {
        vm.setFilterValue(ArtistLibraryFilters.MONITORED, FilterValue.Toggle(true))
        val presentation = vm.state.value.presentation
        assertEquals(listOf(1, 2), presentation.visible.map { it.id })
        assertTrue(presentation.endReached)
    }

    @Test
    fun `changing any filter detaches from the active saved filter`() = runVm {
        vm.setFilterValue(ArtistLibraryFilters.MONITORED, FilterValue.Toggle(true))
        vm.saveFilter("Moni")
        advanceUntilIdle()
        assertEquals("Moni", vm.state.value.savedFilters.single().label)
        assertEquals(vm.state.value.savedFilters.single().id, vm.state.value.activeSavedFilterId)
        vm.setFilterValue(ArtistLibraryFilters.MISSING, FilterValue.Toggle(true))
        assertEquals(null, vm.state.value.activeSavedFilterId)
    }

    @Test
    fun `applying a saved filter twice toggles it off`() = runVm {
        vm.setFilterValue(ArtistLibraryFilters.MONITORED, FilterValue.Toggle(true))
        vm.saveFilter("Moni")
        advanceUntilIdle()
        val id = vm.state.value.savedFilters.single().id
        vm.applySavedFilter(id)
        assertEquals(listOf(1, 2, 3), vm.state.value.presentation.visible.map { it.id })
        vm.applySavedFilter(id)
        assertEquals(listOf(1, 2), vm.state.value.presentation.visible.map { it.id })
    }

    @Test
    fun `loadMore grows the window until the end`() = runVm {
        repeat(120) { index ->
            fake.artists[100 + index] = ArtistResource(
                id = 100 + index,
                artistName = "Bulk $index",
                sortName = "bulk $index",
            )
        }
        vm.refresh()
        advanceUntilIdle()
        assertEquals(ArtistPresentation.INITIAL_COUNT, vm.state.value.presentation.visible.size)
        vm.loadMore()
        assertEquals(ArtistPresentation.INITIAL_COUNT + ArtistPresentation.PAGE_COUNT, vm.state.value.visibleCount)
        assertTrue(vm.state.value.presentation.visible.size <= vm.state.value.artists.size)
    }

    @Test
    fun `selection toggles, select all, and bulk targets`() = runVm {
        vm.setFilterValue(ArtistLibraryFilters.MONITORED, FilterValue.Toggle(true))
        vm.enterSelectMode(1)
        vm.toggleSelected(2)
        assertEquals(setOf(1, 2), vm.state.value.selection)
        assertEquals(listOf(1, 2), vm.state.value.bulkTargets)
        vm.selectAllPresented()
        assertEquals(setOf(1, 2), vm.state.value.selection)
        vm.exitSelectMode()
        assertEquals(emptySet<Int>(), vm.state.value.selection)
        // Without selection the bulk targets cover everything presented.
        assertEquals(listOf(1, 2), vm.state.value.bulkTargets)
    }

    @Test
    fun `editor submit syncs and closes the sheet`() = runVm {
        vm.openEditor()
        vm.updateEditor { it.copy(monitored = false) }
        vm.submitEditor()
        advanceUntilIdle()
        assertEquals(null, vm.state.value.editorForm)
        assertEquals(LibraryMessage.EditSynced, vm.state.value.message)
        assertEquals(false, vm.state.value.artist_monitoredOf(1))
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `editor submit keeps shared monitor strategy when unchanged`() = runVm {
        vm.openEditor()
        vm.updateEditor { it.copy(monitorStrategy = MonitorStrategy.NEW) }
        vm.submitEditor()
        advanceUntilIdle()
        assertEquals(NewItemMonitorTypes.NEW, vm.state.value.artist_monitorNewItemsOf(1))
        vm.openEditor()
        vm.updateEditor { it.copy(monitored = true) }
        vm.submitEditor()
        advanceUntilIdle()
        // All targets now share NEW, so "unchanged" resolves to NEW instead of ALL.
        assertEquals(NewItemMonitorTypes.NEW, vm.state.value.artist_monitorNewItemsOf(1))
    }

    @Test
    fun `editor submit offline queues and flags the affected artists`() = runVm {
        vm.openEditor()
        vm.updateEditor { it.copy(tagsText = "7, 8") }
        fake.error = { _ -> IOException("offline") }
        vm.submitEditor()
        advanceUntilIdle()
        assertEquals(LibraryMessage.EditQueued, vm.state.value.message)
        assertEquals(1, vm.state.value.queuedForServer)
        assertEquals(setOf(1, 2, 3), vm.state.value.queuedArtistIds)
        val action = queue.pending().single()
        assertTrue(action.bodyJson!!.contains("\"tags\":[7,8]"))
    }

    @Test
    fun `editor missing strategy enqueues both writes offline`() = runVm {
        vm.openEditor()
        vm.updateEditor { it.copy(monitorStrategy = MonitorStrategy.MISSING) }
        fake.error = { _ -> IOException("offline") }
        vm.submitEditor()
        advanceUntilIdle()
        assertEquals(LibraryMessage.EditQueued, vm.state.value.message)
        assertEquals(
            listOf("PUT api/v1/artist/editor", "POST api/v1/albumstudio"),
            queue.pending().map { "${it.method} ${it.path}" },
        )
    }

    @Test
    fun `invalid tag text keeps the sheet open with an error`() = runVm {
        vm.openEditor()
        vm.updateEditor { it.copy(tagsText = "rock") }
        vm.submitEditor()
        advanceUntilIdle()
        assertNotNull(vm.state.value.editorForm)
        assertTrue(vm.state.value.editorForm!!.tagsError)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `delete offline queues, clears selection, and reports`() = runVm {
        vm.enterSelectMode(2)
        fake.error = { _ -> IOException("offline") }
        vm.requestDelete()
        vm.confirmDelete(deleteFiles = true, addImportExclusion = false)
        advanceUntilIdle()
        assertEquals(LibraryMessage.DeleteQueued, vm.state.value.message)
        assertEquals(emptySet<Int>(), vm.state.value.selection)
        assertEquals("DELETE", queue.pending().single().method)
    }

    @Test
    fun `commands scope to the selection when one is active`() = runTest {
        var dispatched: Pair<String, List<Int>?>? = null
        fake = FakeLidarrLibrary()
        store = FakeEntityStore()
        queue = FakeActionQueue()
        fake.artists[3] = ArtistResource(id = 3, artistName = "Gamma", sortName = "gamma")
        val repository = LidarrArtistsRepository(
            serverId = ServerId("s1"),
            store = store,
            artistApi = fake,
            artistEditorApi = fake,
            albumApi = fake,
            albumStudioApi = fake,
            qualityProfileApi = { emptyList() },
            metadataProfileApi = { emptyList() },
            commandRunner = ArtistCommandRunner { command, ids ->
                dispatched = command to ids
                ArrResult.ok(Unit)
            },
            actionQueue = queue,
            ioContext = Dispatchers.Unconfined,
        )
        val scoped = ArtistLibraryViewModel(repository, SavedArtistFiltersStore(store, ServerId("s1")), { now })
        val job = launch { scoped.lce.collect {} }
        advanceUntilIdle()
        scoped.enterSelectMode(3)
        scoped.refreshMetadata()
        advanceUntilIdle()
        assertEquals("RefreshArtist" to listOf(3), dispatched)
        scoped.scanDisk()
        advanceUntilIdle()
        assertEquals("RescanArtist" to listOf(3), dispatched)
        job.cancel()
    }

    @Test
    fun `sort flips direction without changing the filter`() = runVm {
        vm.setSort(ArtistLibrarySorts.ALBUMS)
        val ascending = vm.state.value.presentation.all.map { it.id }
        vm.toggleSortDescending()
        assertEquals(ascending.reversed(), vm.state.value.presentation.visible.map { it.id })
    }

    private fun ArtistLibraryUiState.artist_monitoredOf(id: Int): Boolean =
        artists.first { it.id == id }.monitored

    private fun ArtistLibraryUiState.artist_monitorNewItemsOf(id: Int): NewItemMonitorTypes =
        artists.first { it.id == id }.monitorNewItems
}
