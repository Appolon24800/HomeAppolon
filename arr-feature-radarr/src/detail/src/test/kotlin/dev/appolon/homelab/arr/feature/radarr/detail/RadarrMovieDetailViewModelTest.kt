package dev.appolon.homelab.arr.feature.radarr.detail

import dev.appolon.homelab.arr.api.radarr.model.CreditResource
import dev.appolon.homelab.arr.api.radarr.model.CreditType
import dev.appolon.homelab.arr.api.radarr.model.ExtraFileResource
import dev.appolon.homelab.arr.api.radarr.model.HistoryResource
import dev.appolon.homelab.arr.api.radarr.model.MediaCover
import dev.appolon.homelab.arr.api.radarr.model.MediaCoverTypes
import dev.appolon.homelab.arr.api.radarr.model.MovieFileResource
import dev.appolon.homelab.arr.api.radarr.model.MovieHistoryEventType
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
class RadarrMovieDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val serverId = ServerId("server-1")
    private lateinit var connection: FakeRadarrDetailConnection
    private lateinit var store: FakeEntityStore
    private lateinit var queue: ActionQueueImpl
    private lateinit var repository: RadarrMovieDetailRepository

    private val movie = MovieResource(
        id = 1,
        title = "Sunshine",
        year = 2007,
        monitored = false,
        path = "/movies/Sunshine (2007)",
        images = listOf(
            MediaCover(coverType = MediaCoverTypes.POSTER, url = "/api/v3/mediacover/1/poster.jpg"),
        ),
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

    private fun viewModel() = RadarrMovieDetailViewModel(
        serverId = serverId,
        movieId = 1,
        repository = repository,
        queue = queue,
    )

    @Test
    fun `loads every section from the fakes`() = runTest(dispatcher) {
        connection.library.movieFiles += MovieFileResource(
            id = 10,
            movieId = 1,
            relativePath = "Sunshine.mkv",
            size = 2_000L,
            quality = QualityModel(quality = Quality(id = 1, name = "Bluray-1080p")),
        )
        connection.library.credits += CreditResource(
            id = 30,
            personName = "Actor One",
            movieMetadataId = 1,
            type = CreditType.CAST,
            order = 1,
        )
        connection.queue.historyItems += HistoryResource(
            id = 40,
            movieId = 1,
            eventType = MovieHistoryEventType.GRABBED,
            sourceTitle = "Sunshine.2007",
        )
        connection.library.extraFiles += ExtraFileResource(id = 50, movieId = 1)

        val vm = viewModel()
        advanceUntilIdle()

        val movieState = vm.state.value.movie as LceState.Content
        assertEquals("Sunshine", movieState.data.title)
        assertEquals(
            FakeRadarrDetailConnection.BASE_URL + "api/v3/mediacover/1/poster.jpg",
            movieState.data.posterUrl,
        )
        val files = vm.state.value.files as LceState.Content
        assertEquals(1, files.data.size)
        assertEquals("BluRay 1080p", files.data.single().qualityName?.let { dev.appolon.homelab.arr.core.Humanize.qualityName(it) })
        assertEquals(1, (vm.state.value.cast as LceState.Content).data.size)
        assertEquals(0, (vm.state.value.crew as LceState.Content).data.size)
        assertEquals(1, (vm.state.value.history as LceState.Content).data.size)
        assertEquals(1, (vm.state.value.extraFiles as LceState.Content).data.size)
    }

    @Test
    fun `toggle monitored offline flips the header and badges the queue`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        connection.networkUp = false

        vm.toggleMonitored()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue((state.movie as LceState.Content).data.monitored)
        assertEquals(1, state.queuedActions.size)
        assertEquals(DetailMessage.EditQueued, state.message)
    }

    @Test
    fun `toggle monitored online updates without queueing`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggleMonitored()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue((state.movie as LceState.Content).data.monitored)
        assertTrue(state.queuedActions.isEmpty())
        assertEquals(DetailMessage.EditSaved, state.message)
        assertTrue(connection.library.movies.single().monitored)
    }

    @Test
    fun `delete movie online reports deleted`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.deleteMovie(deleteFiles = true, addImportExclusion = false)
        advanceUntilIdle()

        assertEquals(DetailMessage.Deleted, vm.state.value.message)
        assertTrue(connection.library.movies.isEmpty())
        assertTrue(connection.library.movieFiles.none { it.movieId == 1 })
    }

    @Test
    fun `search offline queues a command and reports queued`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        connection.networkUp = false

        vm.startSearch()
        advanceUntilIdle()

        assertEquals(DetailMessage.SearchQueued, vm.state.value.message)
        val pending = queue.pending()
        assertEquals(1, pending.size)
        assertTrue(pending.single().bodyJson!!.contains("MoviesSearch"))
    }

    @Test
    fun `rename online reports started`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.startRename()
        advanceUntilIdle()

        assertEquals(DetailMessage.RenameStarted, vm.state.value.message)
        assertEquals(
            "RenameFiles",
            connection.commandBodies.single()["name"]?.toString()?.trim('"'),
        )
    }

    @Test
    fun `consume message clears it`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.startSearch()
        advanceUntilIdle()
        assertTrue(vm.state.value.message != null)
        vm.consumeMessage()
        assertEquals(null, vm.state.value.message)
    }
}
