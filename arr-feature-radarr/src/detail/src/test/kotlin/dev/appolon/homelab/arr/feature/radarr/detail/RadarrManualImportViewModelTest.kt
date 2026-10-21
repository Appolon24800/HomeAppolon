package dev.appolon.homelab.arr.feature.radarr.detail

import dev.appolon.homelab.arr.api.radarr.model.ManualImportResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.Quality
import dev.appolon.homelab.arr.api.radarr.model.QualityModel
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import dev.appolon.homelab.arr.data.testing.FakeEntityStore

@OptIn(ExperimentalCoroutinesApi::class)
class RadarrManualImportViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val serverId = ServerId("server-1")
    private lateinit var connection: FakeRadarrDetailConnection
    private lateinit var store: FakeEntityStore
    private lateinit var queue: ActionQueueImpl
    private lateinit var repository: RadarrMovieDetailRepository

    private val movie = MovieResource(
        id = 1,
        title = "Sunshine",
        path = "/movies/Sunshine (2007)",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        connection = FakeRadarrDetailConnection()
        store = FakeEntityStore().apply { clock = { 0L } }
        queue = ActionQueueImpl(store)
        repository = RadarrMovieDetailRepository(connection, store, queue, nowMillis = { 0L })
        connection.library.movies += movie
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun importItem(id: Int) = ManualImportResource(
        id = id,
        path = "/movies/Sunshine (2007)/file-$id.mkv",
        relativePath = "file-$id.mkv",
        size = 1_000L * id,
        movie = movie,
        quality = QualityModel(quality = Quality(id = 1, name = "WEBDL-1080p")),
    )

    @Test
    fun `lists unimported candidates inside the movie folder`() = runTest(dispatcher) {
        connection.queue.importItems += importItem(1)
        connection.queue.importItems += importItem(2)
        // Belongs to another movie's folder and must not appear.
        connection.queue.importItems += importItem(3).copy(
            path = "/movies/Other (2001)/file-3.mkv",
        )

        val vm = RadarrManualImportViewModel(serverId, 1, repository)
        advanceUntilIdle()

        val items = (vm.state.value.items as LceState.Content).data
        assertEquals(listOf(1, 2), items.map { it.id })
        assertEquals("/movies/Sunshine (2007)", vm.state.value.folder)
        assertTrue(items.all { it.selected })
    }

    @Test
    fun `toggle and select all drive the selection`() = runTest(dispatcher) {
        connection.queue.importItems += importItem(1)
        connection.queue.importItems += importItem(2)
        val vm = RadarrManualImportViewModel(serverId, 1, repository)
        advanceUntilIdle()

        vm.toggle(1)
        advanceUntilIdle()
        assertEquals(
            listOf(false, true),
            (vm.state.value.items as LceState.Content).data.map { it.selected },
        )

        vm.selectAll(false)
        advanceUntilIdle()
        assertTrue(
            (vm.state.value.items as LceState.Content).data.none { it.selected },
        )
    }

    @Test
    fun `import starts a folder scan command`() = runTest(dispatcher) {
        connection.queue.importItems += importItem(1)
        val vm = RadarrManualImportViewModel(serverId, 1, repository)
        advanceUntilIdle()

        vm.importSelected()
        advanceUntilIdle()

        assertEquals(ImportMessage.ImportStarted, vm.state.value.message)
        val body = connection.commandBodies.single()
        assertEquals("DownloadedMoviesScan", body["name"]?.toString()?.trim('"'))
        assertEquals("\"/movies/Sunshine (2007)\"", body["path"]?.toString())
    }

    @Test
    fun `offline import queues the scan for replay`() = runTest(dispatcher) {
        // Cache the movie first so the import screen knows its folder.
        repository.movie(serverId, 1, force = true)
        connection.networkUp = false

        connection.queue.importItems += importItem(1)
        val vm = RadarrManualImportViewModel(serverId, 1, repository)
        advanceUntilIdle()

        vm.importSelected()
        advanceUntilIdle()

        assertEquals(ImportMessage.ImportQueued, vm.state.value.message)
        val action = queue.pending().single()
        assertEquals("api/v3/command", action.path)
        assertTrue(action.bodyJson!!.contains("DownloadedMoviesScan"))
    }

    @Test
    fun `movie without a folder shows the empty state`() = runTest(dispatcher) {
        connection.library.movies[0] = movie.copy(path = null)
        val vm = RadarrManualImportViewModel(serverId, 1, repository)
        advanceUntilIdle()
        assertEquals(LceState.Empty, vm.state.value.items)
    }
}
