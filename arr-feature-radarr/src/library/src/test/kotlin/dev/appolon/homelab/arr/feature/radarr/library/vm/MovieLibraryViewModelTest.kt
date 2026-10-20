package dev.appolon.homelab.arr.feature.radarr.library.vm

import dev.appolon.homelab.arr.api.radarr.fake.FakeRadarrLibrary
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.QualityProfileResource
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.FilterValue
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.library.FakeActionQueue
import dev.appolon.homelab.arr.feature.radarr.library.FakeEntityStore
import dev.appolon.homelab.arr.feature.radarr.library.data.MovieLibraryFilters
import dev.appolon.homelab.arr.feature.radarr.library.data.MovieLibrarySorts
import dev.appolon.homelab.arr.feature.radarr.library.data.MovieCommandRunner
import dev.appolon.homelab.arr.feature.radarr.library.data.MoviePresentation
import dev.appolon.homelab.arr.feature.radarr.library.data.RadarrMoviesRepository
import dev.appolon.homelab.arr.feature.radarr.library.data.SavedMovieFiltersStore
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
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
class MovieLibraryViewModelTest {

    private val main = UnconfinedTestDispatcher()

    private lateinit var fake: FakeRadarrLibrary
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeActionQueue
    private lateinit var vm: MovieLibraryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newFakes() {
        fake = FakeRadarrLibrary()
        store = FakeEntityStore()
        queue = FakeActionQueue()
        fake.movies += listOf(
            MovieResource(
                id = 1,
                title = "Alpha",
                sortTitle = "alpha",
                year = 2001,
                monitored = true,
                hasFile = true,
            ),
            MovieResource(
                id = 2,
                title = "Beta",
                sortTitle = "beta",
                year = 2002,
                monitored = true,
                hasFile = false,
            ),
            MovieResource(
                id = 3,
                title = "Gamma",
                sortTitle = "gamma",
                year = 2003,
                monitored = false,
                hasFile = true,
            ),
        )
    }

    private fun newVm(): MovieLibraryViewModel {
        val repository = RadarrMoviesRepository(
            serverId = ServerId("s1"),
            store = store,
            movieApi = fake.movie,
            movieEditorApi = fake.movieEditor,
            qualityProfileApi = { listOf(QualityProfileResource(id = 1, name = "Any")) },
            commandRunner = MovieCommandRunner { _, _ -> ArrResult.ok(Unit) },
            actionQueue = queue,
            io = main,
        )
        return MovieLibraryViewModel(repository, SavedMovieFiltersStore(store, ServerId("s1")))
    }

    private fun build(): MovieLibraryViewModel {
        newFakes()
        return newVm()
    }

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
        assertEquals(3, vm.state.value.movies.size)
        assertFalse((lce as LceState.Content).refreshing)
    }

    @Test
    fun `empty library renders the empty state`() = runTest {
        newFakes()
        fake.movies.clear()
        vm = newVm()
        val job = launch { vm.lce.collect {} }
        advanceUntilIdle()
        assertTrue(vm.lce.value is LceState.Empty)
        job.cancel()
    }

    @Test
    fun `refresh failure with no cache renders the error state`() = runTest {
        newFakes()
        fake.error = IOException("offline")
        vm = newVm()
        val job = launch { vm.lce.collect {} }
        advanceUntilIdle()
        assertTrue(vm.lce.value is LceState.Error)
        job.cancel()
    }

    @Test
    fun `filters narrow the presentation and reset the window`() = runVm {
        vm.setFilterValue(MovieLibraryFilters.MISSING, FilterValue.Toggle(true))
        val presentation = vm.state.value.presentation
        assertEquals(listOf(2), presentation.visible.map { it.id })
        assertTrue(presentation.endReached)
    }

    @Test
    fun `changing any filter detaches from the active saved filter`() = runVm {
        vm.setFilterValue(MovieLibraryFilters.MONITORED, FilterValue.Toggle(true))
        vm.saveFilter("Moni")
        advanceUntilIdle()
        assertEquals("Moni", vm.state.value.savedFilters.single().label)
        assertEquals(vm.state.value.savedFilters.single().id, vm.state.value.activeSavedFilterId)
        vm.setFilterValue(MovieLibraryFilters.MISSING, FilterValue.Toggle(true))
        assertEquals(null, vm.state.value.activeSavedFilterId)
    }

    @Test
    fun `applying a saved filter twice toggles it off`() = runVm {
        vm.setFilterValue(MovieLibraryFilters.MONITORED, FilterValue.Toggle(true))
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
            fake.movies += MovieResource(
                id = 100 + index,
                title = "Bulk $index",
                sortTitle = "bulk $index",
            )
        }
        vm.refresh()
        advanceUntilIdle()
        assertEquals(MoviePresentation.INITIAL_COUNT, vm.state.value.presentation.visible.size)
        vm.loadMore()
        val grown = MoviePresentation.INITIAL_COUNT + MoviePresentation.PAGE_COUNT
        assertEquals(grown, vm.state.value.visibleCount)
        assertTrue(vm.state.value.presentation.visible.size <= vm.state.value.movies.size)
    }

    @Test
    fun `selection toggles, select all, and bulk targets`() = runVm {
        vm.setFilterValue(MovieLibraryFilters.MONITORED, FilterValue.Toggle(true))
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
        assertEquals(false, vm.state.value.movies.first { it.id == 1 }.monitored)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `editor submit offline queues and flags the affected movies`() = runVm {
        vm.openEditor()
        vm.updateEditor { it.copy(tagsText = "7, 8") }
        fake.error = IOException("offline")
        vm.submitEditor()
        advanceUntilIdle()
        assertEquals(LibraryMessage.EditQueued, vm.state.value.message)
        assertEquals(1, vm.state.value.queuedForServer)
        assertEquals(setOf(1, 2, 3), vm.state.value.queuedMovieIds)
        val action = queue.pending().single()
        assertTrue(action.bodyJson!!.contains("\"tags\":[7,8]"))
    }

    @Test
    fun `invalid tag text keeps the sheet open with an error`() = runVm {
        vm.openEditor()
        vm.updateEditor { it.copy(tagsText = "drama") }
        vm.submitEditor()
        advanceUntilIdle()
        assertNotNull(vm.state.value.editorForm)
        assertTrue(vm.state.value.editorForm!!.tagsError)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `delete offline queues, clears selection, and reports`() = runVm {
        vm.enterSelectMode(2)
        fake.error = IOException("offline")
        vm.requestDelete()
        vm.confirmDelete(deleteFiles = true, addImportExclusion = false)
        advanceUntilIdle()
        assertEquals(LibraryMessage.DeleteQueued, vm.state.value.message)
        assertEquals(emptySet<Int>(), vm.state.value.selection)
        assertEquals("DELETE", queue.pending().single().method)
    }

    @Test
    fun `commands scope to the selection when one is active`() = runVm {
        var dispatched: Pair<String, List<Int>?>? = null
        val repository = RadarrMoviesRepository(
            serverId = ServerId("s1"),
            store = store,
            movieApi = fake.movie,
            movieEditorApi = fake.movieEditor,
            qualityProfileApi = { emptyList() },
            commandRunner = MovieCommandRunner { command, ids ->
                dispatched = command to ids
                ArrResult.ok(Unit)
            },
            actionQueue = queue,
            io = main,
        )
        val saved = SavedMovieFiltersStore(store, ServerId("s1"))
        val scoped = MovieLibraryViewModel(repository, saved)
        val job = launch { scoped.lce.collect {} }
        advanceUntilIdle()
        scoped.enterSelectMode(3)
        scoped.refreshMetadata()
        advanceUntilIdle()
        assertEquals("RefreshMovie" to listOf(3), dispatched)
        scoped.scanDisk()
        advanceUntilIdle()
        assertEquals("RescanMovie" to listOf(3), dispatched)
        job.cancel()
    }

    @Test
    fun `sort flips direction without changing the filter`() = runVm {
        vm.setFilterValue(MovieLibraryFilters.MONITORED, FilterValue.Toggle(true))
        vm.setSort(MovieLibrarySorts.YEAR)
        vm.toggleSortDescending()
        assertEquals(listOf(2, 1), vm.state.value.presentation.visible.map { it.id })
        vm.toggleSortDescending()
        assertEquals(listOf(1, 2), vm.state.value.presentation.visible.map { it.id })
    }
}
