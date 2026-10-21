package dev.appolon.homelab.arr.feature.radarr.addnew.vm

import dev.appolon.homelab.arr.api.radarr.FakeRadarrCalendar
import dev.appolon.homelab.arr.api.radarr.FakeRadarrDiscovery
import dev.appolon.homelab.arr.api.radarr.FakeRadarrSystem
import dev.appolon.homelab.arr.api.radarr.fake.FakeRadarrLibrary
import dev.appolon.homelab.arr.api.radarr.model.ImportListMoviesResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.MonitorTypes
import dev.appolon.homelab.arr.api.radarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.radarr.model.RootFolderResource
import dev.appolon.homelab.arr.api.radarr.model.TagResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.addnew.FakeActionQueue
import dev.appolon.homelab.arr.feature.radarr.addnew.FakeEntityStore
import dev.appolon.homelab.arr.feature.radarr.addnew.data.RadarrAddNewRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddNewViewModelTest {

    private val main = UnconfinedTestDispatcher()
    private val serverId = ServerId("s1")

    private lateinit var library: FakeRadarrLibrary
    private lateinit var discovery: FakeRadarrDiscovery
    private lateinit var system: FakeRadarrSystem
    private lateinit var calendar: FakeRadarrCalendar
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeActionQueue
    private lateinit var repository: RadarrAddNewRepository
    private lateinit var vm: AddNewViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm(): AddNewViewModel {
        library = FakeRadarrLibrary()
        discovery = FakeRadarrDiscovery()
        system = FakeRadarrSystem()
        calendar = FakeRadarrCalendar()
        store = FakeEntityStore()
        queue = FakeActionQueue()
        library.movies += MovieResource(id = 1, title = "Alpha", tmdbId = 101, year = 2001)
        library.lookupCatalog += listOf(
            MovieResource(title = "Dune", tmdbId = 11, year = 2021),
            MovieResource(title = "Dune", tmdbId = 12, year = 1984),
        )
        system.rootFolderItems += listOf(
            RootFolderResource(id = 1, path = "/media/films", freeSpace = 1_000L),
            RootFolderResource(id = 2, path = "/media/4k", freeSpace = 2_000L),
        )
        calendar.qualityProfiles += listOf(
            QualityProfileResource(id = 1, name = "HD"),
            QualityProfileResource(id = 2, name = "UHD"),
        )
        system.tagItems += TagResource(id = 4, label = "kids")
        discovery.discoveredMovies += listOf(
            ImportListMoviesResource(
                tmdbId = 11,
                title = "Dune",
                year = 2021,
                isRecommendation = true,
            ),
            ImportListMoviesResource(tmdbId = 31, title = "Heat", year = 1995, isPopular = true),
        )
        repository = RadarrAddNewRepository(
            serverId = serverId,
            store = store,
            movieLookupApi = library.movieLookup,
            movieApi = library.movie,
            movieImportApi = library.movieImport,
            importListMoviesApi = discovery,
            rootFolderApi = system,
            tagApi = system,
            qualityProfileApi = calendar,
            actionQueue = queue,
            io = main,
        )
        return AddNewViewModel(repository)
    }

    private fun runVm(block: suspend kotlinx.coroutines.test.TestScope.() -> Unit) = runTest {
        vm = newVm()
        val job = launch { vm.state.collect {} }
        advanceUntilIdle()
        block()
        job.cancel()
    }

    @Test
    fun `init loads catalogs, discovery, and library flags`() = runVm {
        val state = vm.state.value
        assertEquals(listOf("/media/films", "/media/4k"), state.rootFolders.map { it.path })
        assertEquals(listOf("HD", "UHD"), state.qualityProfiles.map { it.name })
        assertEquals(listOf("kids"), state.tags.map { it.label })
        assertTrue(state.existingTmdbIds.contains(101))
        val content = state.discoveryLce
        assertTrue(content is LceState.Content)
        assertEquals(2, (content as LceState.Content).data.size)
    }

    @Test
    fun `search parses the year and ranks the matching year first`() = runVm {
        vm.onQueryChange("Dune (1984)")
        assertEquals(1984, vm.state.value.parsedYear)
        vm.submitSearch()
        val results = vm.state.value.searchResults
        assertEquals(listOf(1984, 2021), results.map { it.year })
        assertTrue(vm.state.value.searchActive)
    }

    @Test
    fun `blank query resets the results area`() = runVm {
        vm.onQueryChange("Dune")
        vm.submitSearch()
        assertTrue(vm.state.value.searched)
        vm.onQueryChange("")
        assertFalse(vm.state.value.searchActive)
        assertFalse(vm.state.value.searched)
        assertNull(vm.state.value.searchLce)
    }

    @Test
    fun `openAddSheet preselects the first root folder and profile`() = runVm {
        vm.openAddSheet(library.lookupCatalog.first())
        val sheet = vm.state.value.addSheet
        assertEquals("/media/films", sheet?.rootFolderPath)
        assertEquals(1, sheet?.qualityProfileId)
        assertTrue(sheet?.submittable == true)
    }

    @Test
    fun `submitAdd add path posts and closes the sheet`() = runVm {
        vm.openAddSheet(library.lookupCatalog.first { it.tmdbId == 11 })
        vm.updateAddSheet { it.copy(selectedTags = setOf(4)) }
        vm.submitAdd(searchForMovie = false)
        val state = vm.state.value
        assertNull(state.addSheet)
        assertEquals(AddNewMessage.Added("Dune"), state.message)
        val added = library.movies.first { it.tmdbId == 11 }
        assertEquals(setOf(4), added.tags)
        assertEquals(false, added.addOptions?.searchForMovie)
    }

    @Test
    fun `submitAdd with search reports the search`() = runVm {
        vm.openAddSheet(library.lookupCatalog.first { it.tmdbId == 11 })
        vm.submitAdd(searchForMovie = true)
        assertEquals(
            AddNewMessage.AddedAndSearching("Dune"),
            vm.state.value.message,
        )
        assertEquals(true, library.movies.first { it.tmdbId == 11 }.addOptions?.searchForMovie)
    }

    @Test
    fun `submitAdd offline queues and flags the movie`() = runVm {
        library.error = IOException("plane mode")
        vm.openAddSheet(library.lookupCatalog.first { it.tmdbId == 11 })
        vm.submitAdd(searchForMovie = false)
        val state = vm.state.value
        assertEquals(AddNewMessage.AddQueued("Dune"), state.message)
        assertNull(state.addSheet)
        assertEquals(setOf(11), state.queuedTmdbIds)
        assertEquals(1, state.pendingCount)
        // The optimistic row makes the result show as existing-in-library.
        assertTrue(state.existingTmdbIds.contains(11))
        assertEquals(1, queue.pending().size)
    }

    @Test
    fun `monitor strategy NONE means unmonitored payload`() = runVm {
        vm.openAddSheet(library.lookupCatalog.first { it.tmdbId == 11 })
        vm.updateAddSheet { it.copy(monitor = MonitorTypes.NONE) }
        vm.submitAdd(searchForMovie = false)
        assertEquals(false, library.movies.first { it.tmdbId == 11 }.monitored)
    }

    @Test
    fun `consumeMessage clears the snackbar state`() = runVm {
        vm.openAddSheet(library.lookupCatalog.first { it.tmdbId == 11 })
        vm.submitAdd(searchForMovie = false)
        assertTrue(vm.state.value.message != null)
        vm.consumeMessage()
        assertNull(vm.state.value.message)
    }

    @Test
    fun `setTab switches tabs and refresh refreshes discovery`() = runVm {
        vm.setTab(AddNewTab.COLLECTIONS)
        assertEquals(AddNewTab.COLLECTIONS, vm.state.value.tab)
        discovery.discoveredMovies.clear()
        vm.refreshAll()
        val content = vm.state.value.discoveryLce
        // The cleared feed falls back to the cached copy; the cache survives.
        assertTrue(content is LceState.Content)
    }
}
