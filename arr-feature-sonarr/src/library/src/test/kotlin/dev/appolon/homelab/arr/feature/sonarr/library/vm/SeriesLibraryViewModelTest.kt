package dev.appolon.homelab.arr.feature.sonarr.library.vm

import dev.appolon.homelab.arr.api.sonarr.FakeSonarrLibrary
import dev.appolon.homelab.arr.api.sonarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.SeasonResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesStatisticsResource
import dev.appolon.homelab.arr.api.sonarr.model.TagResource
import dev.appolon.homelab.arr.core.FilterValue
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.library.FakeActionQueue
import dev.appolon.homelab.arr.feature.sonarr.library.FakeEntityStore
import dev.appolon.homelab.arr.feature.sonarr.library.data.SavedSeriesFiltersStore
import dev.appolon.homelab.arr.feature.sonarr.library.data.SeriesLibraryFilters
import dev.appolon.homelab.arr.feature.sonarr.library.data.SonarrSeriesRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the ViewModel's LCE mapping, filter and saved-filter handling,
 * selection, and every bulk write path including offline queueing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SeriesLibraryViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val fake = FakeSonarrLibrary()
    private val store = FakeEntityStore()
    private val queue = FakeActionQueue()
    private val serverId = ServerId("server-1")

    private val repository = SonarrSeriesRepository(
        serverId = serverId,
        store = store,
        seriesApi = fake.series,
        seriesEditorApi = fake.seriesEditor,
        seasonPassApi = fake.seasonPass,
        qualityProfileApi = { listOf(QualityProfileResource(id = 1, name = "Any HD")) },
        tagApi = { listOf(TagResource(id = 10, label = "kids")) },
        commandRunner = { _, _ -> dev.appolon.homelab.arr.core.ArrResult.ok(Unit) },
        actionQueue = queue,
        ioDispatcher = dispatcher,
    )

    private val savedFilters = SavedSeriesFiltersStore(store, serverId)
    private lateinit var vm: SeriesLibraryViewModel
    private var latestLce: LceState<SeriesLibraryUiState> = LceState.Loading
    private val collectors = CoroutineScope(dispatcher + SupervisorJob())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        fake.seriesStore[1] = series(1, "Alpha", files = 8, episodes = 10)
        fake.seriesStore[2] = series(2, "Beta", monitored = false, files = 0, episodes = 10)
        vm = SeriesLibraryViewModel(repository, savedFilters)
        // stateIn only computes while subscribed; keep one eager collector.
        collectors.launch { vm.lce.collect { latestLce = it } }
    }

    @After
    fun tearDown() {
        collectors.cancel()
        Dispatchers.resetMain()
    }

    /** stateIn only computes while subscribed; keep one eager empty collector. */
    private fun subscribeLce(viewModel: SeriesLibraryViewModel) {
        collectors.launch { viewModel.lce.collect {} }
    }

    private fun series(
        id: Int,
        title: String,
        monitored: Boolean = true,
        episodes: Int = 10,
        files: Int = 10,
    ) = SeriesResource(
        id = id,
        title = title,
        monitored = monitored,
        qualityProfileId = 1,
        seasons = listOf(SeasonResource(seasonNumber = 1, monitored = true)),
        statistics = SeriesStatisticsResource(
            seasonCount = 1,
            episodeCount = episodes,
            episodeFileCount = files,
            totalEpisodeCount = episodes,
        ),
        tags = if (id == 1) setOf(10) else null,
    )

    @Test
    fun `initial state loads content with series`() {
        val state = vm.state.value
        assertEquals(listOf("Alpha", "Beta"), state.series.map { it.title })
        assertEquals(listOf("Any HD"), state.qualityProfiles.map { it.name })
        assertTrue(state.loaded)
        assertTrue(latestLce is LceState.Content)
    }

    @Test
    fun `network failure with empty cache surfaces error`() = runTest {
        val failing = FakeSonarrLibrary(error = { IOException("down") })
        val errorVm = SeriesLibraryViewModel(
            SonarrSeriesRepository(
                serverId = serverId,
                store = FakeEntityStore(),
                seriesApi = failing.series,
                seriesEditorApi = failing.seriesEditor,
                seasonPassApi = failing.seasonPass,
                qualityProfileApi = { emptyList() },
                tagApi = { emptyList() },
                commandRunner = { _, _ -> dev.appolon.homelab.arr.core.ArrResult.ok(Unit) },
                actionQueue = FakeActionQueue(),
                ioDispatcher = dispatcher,
            ),
            SavedSeriesFiltersStore(FakeEntityStore(), serverId),
        )
        subscribeLce(errorVm)
        assertTrue(errorVm.lce.value is LceState.Error)
    }

    @Test
    fun `empty library renders empty state`() = runTest {
        val emptyVm = SeriesLibraryViewModel(
            SonarrSeriesRepository(
                serverId = serverId,
                store = FakeEntityStore(),
                seriesApi = FakeSonarrLibrary().series,
                seriesEditorApi = fake.seriesEditor,
                seasonPassApi = fake.seasonPass,
                qualityProfileApi = { emptyList() },
                tagApi = { emptyList() },
                commandRunner = { _, _ -> dev.appolon.homelab.arr.core.ArrResult.ok(Unit) },
                actionQueue = FakeActionQueue(),
                ioDispatcher = dispatcher,
            ),
            SavedSeriesFiltersStore(FakeEntityStore(), serverId),
        )
        subscribeLce(emptyVm)
        assertTrue(emptyVm.lce.value is LceState.Empty)
    }

    @Test
    fun `missing filter keeps monitored series with missing episodes`() {
        vm.setFilterValue(SeriesLibraryFilters.MISSING, FilterValue.Toggle(true))
        assertEquals(listOf("Alpha"), vm.state.value.presentation.all.map { it.title })
    }

    @Test
    fun `setting the empty value clears the filter`() {
        vm.setFilterValue(SeriesLibraryFilters.MONITORED, FilterValue.Toggle(true))
        vm.setFilterValue(SeriesLibraryFilters.MONITORED, FilterValue.Toggle(null))
        assertEquals(2, vm.state.value.presentation.all.size)
    }

    @Test
    fun `saved filters save apply and delete`() = runTest {
        vm.setFilterValue(SeriesLibraryFilters.MONITORED, FilterValue.Toggle(true))
        vm.saveFilter("Monitored only")
        val saved = vm.state.value.savedFilters.single()
        assertEquals("Monitored only", saved.label)
        assertEquals(saved.id, vm.state.value.activeSavedFilterId)

        vm.clearFilters()
        assertNull(vm.state.value.activeSavedFilterId)
        vm.applySavedFilter(saved.id)
        assertEquals(saved.id, vm.state.value.activeSavedFilterId)
        assertEquals(listOf("Alpha"), vm.state.value.presentation.all.map { it.title })

        vm.deleteSavedFilter(saved.id)
        assertTrue(vm.state.value.savedFilters.isEmpty())
    }

    @Test
    fun `selection targets bulk operations`() {
        vm.enterSelectMode(1)
        vm.toggleSelected(2)
        assertEquals(setOf(1, 2), vm.state.value.selection)
        vm.selectAllPresented()
        assertEquals(setOf(1, 2), vm.state.value.selection)
        assertEquals(listOf(1, 2), vm.state.value.bulkTargets)
        vm.exitSelectMode()
        // Without a selection, bulk targets fall back to everything presented.
        assertEquals(listOf(1, 2), vm.state.value.bulkTargets)
    }

    @Test
    fun `bulk editor submit applies and reports synced`() {
        vm.enterSelectMode(1)
        vm.openEditor()
        vm.updateEditor { it.copy(monitored = false) }
        vm.submitEditor()
        assertEquals(null, vm.state.value.editorForm)
        assertEquals(LibraryMessage.EditSynced, vm.state.value.message)
        assertEquals(false, fake.seriesStore[1]!!.monitored)
    }

    @Test
    fun `bulk editor submit reports queued when offline`() {
        fake.error = { if (it == "SeriesEditor.update") IOException("offline") else null }
        vm.enterSelectMode(1)
        vm.openEditor()
        vm.updateEditor { it.copy(monitored = false) }
        vm.submitEditor()
        assertEquals(LibraryMessage.EditQueued, vm.state.value.message)
        assertEquals(1, queue.enqueued.size)
        // Optimistic UI plus queued indicator.
        assertEquals(false, vm.state.value.series.first { it.id == 1 }.monitored)
        assertEquals(setOf(1), vm.state.value.queuedSeriesIds)
        assertEquals(1, vm.state.value.queuedForServer)
    }

    @Test
    fun `invalid tag text keeps the sheet open with an error`() {
        vm.openEditor()
        vm.updateEditor { it.copy(tagsText = "abc") }
        vm.submitEditor()
        assertTrue(vm.state.value.editorForm?.tagsError == true)
        assertTrue(queue.enqueued.isEmpty())
    }

    @Test
    fun `season pass applies monitoring and reports synced`() {
        vm.enterSelectMode(2)
        vm.openSeasonPass()
        vm.updateSeasonPass { it.copy(monitored = true) }
        vm.submitSeasonPass()
        assertEquals(null, vm.state.value.seasonPassForm)
        assertEquals(LibraryMessage.SeasonPassSynced, vm.state.value.message)
        assertEquals(true, fake.seriesStore[2]!!.monitored)
    }

    @Test
    fun `season pass queues when offline`() {
        fake.error = { if (it == "SeasonPass.create") IOException("offline") else null }
        vm.enterSelectMode(2)
        vm.openSeasonPass()
        vm.updateSeasonPass { it.copy(monitored = true) }
        vm.submitSeasonPass()
        assertEquals(LibraryMessage.SeasonPassQueued, vm.state.value.message)
        assertEquals(true, vm.state.value.series.first { it.id == 2 }.monitored)
    }

    @Test
    fun `delete confirms and clears selection`() {
        vm.enterSelectMode(2)
        vm.requestDelete()
        assertTrue(vm.state.value.confirmDelete)
        vm.confirmDelete(deleteFiles = false, addImportListExclusion = false)
        assertEquals(LibraryMessage.DeleteSynced, vm.state.value.message)
        assertTrue(vm.state.value.selection.isEmpty())
        assertEquals(listOf(1), fake.seriesStore.keys.toList())
    }

    @Test
    fun `commands report started and queue when offline`() {
        vm.refreshMetadata()
        assertEquals(LibraryMessage.CommandStarted, vm.state.value.message)

        fake.error = { null }
        vm.consumeMessage()
        val failingCommand = object : dev.appolon.homelab.arr.feature.sonarr.library.data.SonarrCommandRunner {
            override suspend fun run(command: String, seriesId: Int?) =
                dev.appolon.homelab.arr.core.ArrResult.err(
                    dev.appolon.homelab.arr.core.ArrError.Network(IOException("offline")),
                )
        }
        val queuedVm = SeriesLibraryViewModel(
            SonarrSeriesRepository(
                serverId = serverId,
                store = store,
                seriesApi = fake.series,
                seriesEditorApi = fake.seriesEditor,
                seasonPassApi = fake.seasonPass,
                qualityProfileApi = { emptyList() },
                tagApi = { emptyList() },
                commandRunner = failingCommand,
                actionQueue = queue,
                ioDispatcher = dispatcher,
            ),
            savedFilters,
        )
        queuedVm.scanDisk()
        assertEquals(LibraryMessage.CommandQueued, queuedVm.state.value.message)
    }

    @Test
    fun `loadMore grows the window until the end`() {
        repeat(50) { index ->
            fake.seriesStore[10 + index] = series(10 + index, "Show ${'A' + index}")
        }
        vm.refresh()
        val initial = vm.state.value.presentation.visible.size
        repeat(3) { vm.loadMore() }
        val state = vm.state.value
        assertTrue(state.presentation.visible.size >= initial)
        assertTrue(state.presentation.endReached)
    }

    @Test
    fun `view and sort toggles update state`() {
        vm.setView(LibraryView.TABLE)
        assertEquals(LibraryView.TABLE, vm.state.value.view)
        vm.setSort("year")
        vm.toggleSortDescending()
        assertEquals("year", vm.state.value.sortId)
        assertTrue(vm.state.value.sortDescending)
    }

    @Test
    fun `a fresh ViewModel reports loading until first collection`() {
        val fresh = SeriesLibraryViewModel(repository, savedFilters)
        assertEquals(LceState.Loading, fresh.lce.value)
        collectors.launch { fresh.lce.collect {} }
        assertTrue(fresh.lce.value is LceState.Content)
    }

    @Test
    fun `refresh failure with a warm cache keeps content and records the load error`() = runTest {
        assertTrue(vm.lce.value is LceState.Content)
        fake.error = { _: String -> IOException("down") }
        vm.refresh()
        val state = vm.state.value
        assertTrue(vm.lce.value is LceState.Content)
        assertTrue(state.loadError != null)
        assertEquals(listOf("Alpha", "Beta"), state.series.map { it.title })
    }

    @Test
    fun `retry after a failed load recovers to content and clears the error`() = runTest {
        val failing = FakeSonarrLibrary(error = { IOException("down") })
        val errorVm = SeriesLibraryViewModel(
            SonarrSeriesRepository(
                serverId = serverId,
                store = FakeEntityStore(),
                seriesApi = failing.series,
                seriesEditorApi = failing.seriesEditor,
                seasonPassApi = failing.seasonPass,
                qualityProfileApi = { emptyList() },
                tagApi = { emptyList() },
                commandRunner = { _, _ -> dev.appolon.homelab.arr.core.ArrResult.ok(Unit) },
                actionQueue = FakeActionQueue(),
                ioDispatcher = dispatcher,
            ),
            SavedSeriesFiltersStore(FakeEntityStore(), serverId),
        )
        subscribeLce(errorVm)
        assertTrue(errorVm.lce.value is LceState.Error)

        failing.error = { _: String -> null }
        failing.seriesStore[9] = series(9, "Recovered")
        errorVm.refresh()

        assertTrue(errorVm.lce.value is LceState.Content)
        assertNull(errorVm.state.value.loadError)
        assertEquals(listOf("Recovered"), errorVm.state.value.series.map { it.title })
    }
}
