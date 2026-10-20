package dev.appolon.homelab.arr.feature.radarr.addnew.vm

import dev.appolon.homelab.arr.api.radarr.FakeRadarrCalendar
import dev.appolon.homelab.arr.api.radarr.FakeRadarrDiscovery
import dev.appolon.homelab.arr.api.radarr.FakeRadarrSystem
import dev.appolon.homelab.arr.api.radarr.fake.FakeRadarrLibrary
import dev.appolon.homelab.arr.api.radarr.model.CollectionResource
import dev.appolon.homelab.arr.api.radarr.model.MovieCollectionResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.QualityProfileResource
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.addnew.FakeActionQueue
import dev.appolon.homelab.arr.feature.radarr.addnew.FakeEntityStore
import dev.appolon.homelab.arr.feature.radarr.addnew.data.AddNewCommandRunner
import dev.appolon.homelab.arr.feature.radarr.addnew.data.AddNewLogic
import dev.appolon.homelab.arr.feature.radarr.addnew.data.RadarrAddNewRepository
import dev.appolon.homelab.arr.feature.radarr.addnew.data.RadarrCollectionsRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RadarrCollectionsViewModelTest {

    private val main = UnconfinedTestDispatcher()
    private val serverId = ServerId("s1")

    private lateinit var library: FakeRadarrLibrary
    private lateinit var discovery: FakeRadarrDiscovery
    private lateinit var system: FakeRadarrSystem
    private lateinit var calendar: FakeRadarrCalendar
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeActionQueue
    private lateinit var commands: MutableList<Pair<String, List<Int>?>>
    private var commandResult: ArrResult<Unit> = ArrResult.ok(Unit)
    private lateinit var vm: RadarrCollectionsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm(): RadarrCollectionsViewModel {
        library = FakeRadarrLibrary()
        discovery = FakeRadarrDiscovery()
        system = FakeRadarrSystem()
        calendar = FakeRadarrCalendar()
        store = FakeEntityStore()
        queue = FakeActionQueue()
        commands = mutableListOf()
        library.movies += MovieResource(
            id = 1,
            title = "Dune",
            tmdbId = 11,
            hasFile = false,
            collection = MovieCollectionResource(tmdbId = 10),
        )
        discovery.collections += CollectionResource(
            id = 1,
            title = "Dune Collection",
            tmdbId = 10,
            monitored = false,
            qualityProfileId = 7,
            missingMovies = 1,
        )
        calendar.qualityProfiles += QualityProfileResource(id = 7, name = "Any")
        val addNewRepository = RadarrAddNewRepository(
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
        val collectionsRepository = RadarrCollectionsRepository(
            serverId = serverId,
            store = store,
            collectionApi = discovery,
            movieApi = library.movie,
            commandRunner = AddNewCommandRunner { command, movieIds ->
                commands += command to movieIds
                commandResult
            },
            actionQueue = queue,
            io = main,
        )
        return RadarrCollectionsViewModel(
            repository = collectionsRepository,
            addNewRepository = addNewRepository,
        )
    }

    private fun runVm(block: suspend kotlinx.coroutines.test.TestScope.() -> Unit) = runTest {
        vm = newVm()
        val job = launch { vm.state.collect {} }
        advanceUntilIdle()
        block()
        job.cancel()
    }

    @Test
    fun `init loads collections and quality profiles`() = runVm {
        val state = vm.state.value
        assertTrue(state.lce is LceState.Content)
        assertEquals(listOf("Dune Collection"), state.collections.map { it.title })
        assertEquals("Any", state.profileLabel(7))
    }

    @Test
    fun `toggleMonitored syncs and reports updated`() = runVm {
        vm.toggleMonitored(vm.state.value.collections.single())
        assertEquals(
            CollectionsMessage.Updated("Dune Collection"),
            vm.state.value.message,
        )
        assertEquals(true, discovery.collections.single().monitored)
    }

    @Test
    fun `toggleMonitored offline queues and flags the row`() = runVm {
        discovery.errorFor = { if (it == "updateCollection") IOException("offline") else null }
        vm.toggleMonitored(vm.state.value.collections.single())
        val state = vm.state.value
        assertEquals(CollectionsMessage.UpdateQueued("Dune Collection"), state.message)
        assertEquals(setOf(1), state.queuedCollectionIds)
    }

    @Test
    fun `editor sheet edits monitoring quality and search on add`() = runVm {
        vm.openEditor(vm.state.value.collections.single())
        vm.updateEditor { it.copy(monitored = true, qualityProfileId = 9, searchOnAdd = true) }
        vm.submitEditor()
        val updated = discovery.collections.single()
        assertEquals(true, updated.monitored)
        assertEquals(9, updated.qualityProfileId)
        assertEquals(true, updated.searchOnAdd)
        assertEquals(CollectionsMessage.Updated("Dune Collection"), vm.state.value.message)
        assertNull(vm.state.value.editSheet)
    }

    @Test
    fun `searchMissing dispatches MoviesSearch with missing ids`() = runVm {
        vm.searchMissing(vm.state.value.collections.single())
        assertEquals(
            CollectionsMessage.SearchStarted(1, "Dune Collection"),
            vm.state.value.message,
        )
        assertEquals(listOf(AddNewLogic.COMMAND_MOVIES_SEARCH to listOf(1)), commands)
    }

    @Test
    fun `searchMissing reports nothing to search when complete`() = runVm {
        library.movies.clear()
        library.movies += MovieResource(
            id = 1,
            title = "Dune",
            tmdbId = 11,
            hasFile = true,
            collection = MovieCollectionResource(tmdbId = 10),
        )
        vm.searchMissing(vm.state.value.collections.single())
        assertEquals(
            CollectionsMessage.NothingToSearch("Dune Collection"),
            vm.state.value.message,
        )
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `searchMissing offline queues the command`() = runVm {
        commandResult = ArrResult.err(
            dev.appolon.homelab.arr.core.ArrError.Network(IOException("offline")),
        )
        vm.searchMissing(vm.state.value.collections.single())
        assertEquals(
            CollectionsMessage.SearchQueued("Dune Collection"),
            vm.state.value.message,
        )
        val action = queue.pending().single()
        assertEquals("api/v3/command", action.path)
    }
}
