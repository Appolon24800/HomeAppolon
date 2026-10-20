package dev.appolon.homelab.arr.feature.radarr.queue.vm

import dev.appolon.homelab.arr.api.radarr.FakeRadarrQueue
import dev.appolon.homelab.arr.api.radarr.model.FileSystemEntityType
import dev.appolon.homelab.arr.api.radarr.model.FileSystemModel
import dev.appolon.homelab.arr.api.radarr.model.FileSystemResult
import dev.appolon.homelab.arr.api.radarr.model.ManualImportResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.feature.radarr.queue.data.RadarrManualImportRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ManualImportViewModelTest {

    private lateinit var fake: FakeRadarrQueue
    private lateinit var store: FakeEntityStore

    private fun repository(scheduler: kotlinx.coroutines.test.TestCoroutineScheduler) =
        RadarrManualImportRepository(
            serverId = ServerId("server-1"),
            manualImportApi = fake,
            fileSystemCall = { path, _ ->
                FileSystemResult(
                    parent = path?.let { "/" },
                    directories = listOf(
                        FileSystemModel(
                            type = FileSystemEntityType.FOLDER,
                            name = "movies",
                            path = (path ?: "") + "/movies",
                        ),
                    ),
                )
            },
            actionQueue = ActionQueueImpl(store),
            ioContext = StandardTestDispatcher(scheduler),
        )

    private fun item(
        id: Int,
        movie: MovieResource = MovieResource(id = id + 10, title = "Movie $id"),
        downloadId: String? = "dl-1",
    ) = ManualImportResource(
        id = id,
        path = "/movies/$id.mkv",
        relativePath = "$id.mkv",
        downloadId = downloadId,
        movie = movie,
    )

    @Before
    fun setUp() {
        fake = FakeRadarrQueue()
        store = FakeEntityStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `folder mode browses the root and scans a picked folder`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.importItems += item(1)
        val vm = ManualImportViewModel(repository(testScheduler), downloadId = null, outputPath = null)
        vm.state.collectWhileActive(this)
        advanceUntilIdle()

        assertEquals(1, vm.state.value.folders.size)
        assertEquals(null, vm.state.value.folder)

        vm.loadFolderItems("/movies")
        advanceUntilIdle()

        assertEquals("/movies", vm.state.value.folder)
        assertEquals(listOf(1), vm.state.value.items.map { it.id })
    }

    @Test
    fun `download mode lists the download's items on entry`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.importItems += item(1)
        fake.importItems += item(2, downloadId = "dl-2")
        val vm = ManualImportViewModel(repository(testScheduler), downloadId = "dl-1", outputPath = null)
        vm.state.collectWhileActive(this)
        advanceUntilIdle()

        assertEquals(listOf(1), vm.state.value.items.map { it.id })
    }

    @Test
    fun `selection toggles and refuses imports without a matched movie`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.importItems += item(1)
        fake.importItems += item(2, movie = MovieResource(id = 0))
        val vm = ManualImportViewModel(repository(testScheduler), downloadId = "dl-1", outputPath = null)
        vm.state.collectWhileActive(this)
        advanceUntilIdle()

        vm.selectAll()
        assertFalse(vm.state.value.canImport)
        assertTrue(vm.state.value.selectionIncomplete)

        vm.toggle(2)
        assertTrue(vm.state.value.canImport)
        assertEquals(setOf(1), vm.state.value.selected)
    }

    @Test
    fun `import success reports the imported count`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.importItems += item(1)
        fake.importItems += item(2)
        val vm = ManualImportViewModel(repository(testScheduler), downloadId = "dl-1", outputPath = null)
        vm.state.collectWhileActive(this)
        advanceUntilIdle()

        vm.selectAll()
        vm.importSelected()
        advanceUntilIdle()

        assertEquals(ManualImportNotice.IMPORTED, vm.state.value.notice)
        assertEquals(2, vm.state.value.importedCount)
    }

    @Test
    fun `import offline reports the queued notice`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.importItems += item(1)
        val vm = ManualImportViewModel(repository(testScheduler), downloadId = "dl-1", outputPath = null)
        vm.state.collectWhileActive(this)
        advanceUntilIdle()
        fake.errorFor = { op -> if (op == "reprocessManualImport") IOException("offline") else null }

        vm.selectAll()
        vm.importSelected()
        advanceUntilIdle()

        assertEquals(ManualImportNotice.QUEUED, vm.state.value.notice)
    }

    @Test
    fun `folder errors surface without clearing the previous items`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.importItems += item(1)
        val vm = ManualImportViewModel(repository(testScheduler), downloadId = null, outputPath = null)
        vm.state.collectWhileActive(this)
        advanceUntilIdle()
        vm.loadFolderItems("/movies")
        advanceUntilIdle()
        assertEquals(1, vm.state.value.items.size)

        fake.errorFor = { op -> if (op == "manualImport") IOException("offline") else null }
        vm.loadFolderItems("/other")
        advanceUntilIdle()

        assertTrue(vm.state.value.itemsLoad is ImportLoad.Failed)
        assertTrue(vm.state.value.items.isEmpty())
    }
}

/** Keeps a StateFlow hot for the duration of the test. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> StateFlow<T>.collectWhileActive(scope: TestScope) {
    scope.backgroundScope.launch { this@collectWhileActive.collect { } }
}
