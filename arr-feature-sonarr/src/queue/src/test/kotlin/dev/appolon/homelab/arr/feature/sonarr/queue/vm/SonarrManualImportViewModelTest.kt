package dev.appolon.homelab.arr.feature.sonarr.queue.vm

import dev.appolon.homelab.arr.api.sonarr.FakeSonarrQueue
import dev.appolon.homelab.arr.api.sonarr.model.ManualImportResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.feature.sonarr.queue.FakeEntityStore
import dev.appolon.homelab.arr.feature.sonarr.queue.data.SonarrManualImportRepository
import java.io.IOException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
class SonarrManualImportViewModelTest {

    private lateinit var fake: FakeSonarrQueue
    private lateinit var store: FakeEntityStore

    private fun repository(scheduler: kotlinx.coroutines.test.TestCoroutineScheduler) =
        SonarrManualImportRepository(
            serverId = ServerId("server-1"),
            manualImportApi = fake.manualImport,
            fileSystemCall = { path, _ ->
                val directories = buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "folder")
                            put("name", "tv")
                            put("path", (path ?: "") + "/tv")
                        },
                    )
                }
                buildJsonObject {
                    path?.let { put("parent", "/") }
                    put("directories", directories)
                }
            },
            actionQueue = ActionQueueImpl(store),
            ioContext = StandardTestDispatcher(scheduler),
        )

    private fun item(
        id: Int,
        seriesId: Int = id + 10,
        episodes: List<JsonObject>? = listOf(
            buildJsonObject {
                put("id", id + 100)
                put("seasonNumber", 1)
                put("episodeNumber", id)
            },
        ),
        downloadId: String? = "dl-1",
    ) = ManualImportResource(
        id = id,
        path = "/tv/$id.mkv",
        relativePath = "$id.mkv",
        downloadId = downloadId,
        series = buildJsonObject {
            put("id", seriesId)
            put("title", "Show $seriesId")
        },
        seasonNumber = 1,
        episodes = episodes,
    )

    @Before
    fun setUp() {
        fake = FakeSonarrQueue()
        store = FakeEntityStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `folder mode browses the root and scans a picked folder`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.manualImports += item(1)
        val vm = ManualImportViewModel(repository(testScheduler), downloadId = null, outputPath = null)
        vm.state.collectWhileActive(this)
        advanceUntilIdle()

        assertEquals(1, vm.state.value.folders.size)
        assertEquals(null, vm.state.value.folder)

        vm.loadFolderItems("/tv")
        advanceUntilIdle()

        assertEquals("/tv", vm.state.value.folder)
        assertEquals(listOf(1), vm.state.value.items.map { it.id })
    }

    @Test
    fun `download mode lists the download's items on entry`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.manualImports += item(1)
        fake.manualImports += item(2, downloadId = "dl-2")
        val vm = ManualImportViewModel(repository(testScheduler), downloadId = "dl-1", outputPath = null)
        vm.state.collectWhileActive(this)
        advanceUntilIdle()

        assertEquals(listOf(1), vm.state.value.items.map { it.id })
    }

    @Test
    fun `selection toggles and refuses imports without a matched series`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.manualImports += item(1)
        fake.manualImports += item(2, seriesId = 0)
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
    fun `selection refuses items without episodes`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.manualImports += item(1, episodes = emptyList())
        val vm = ManualImportViewModel(repository(testScheduler), downloadId = "dl-1", outputPath = null)
        vm.state.collectWhileActive(this)
        advanceUntilIdle()

        vm.selectAll()

        assertFalse(vm.state.value.canImport)
    }

    @Test
    fun `import success reports the imported count`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.manualImports += item(1)
        fake.manualImports += item(2)
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
        fake.manualImports += item(1)
        val vm = ManualImportViewModel(repository(testScheduler), downloadId = "dl-1", outputPath = null)
        vm.state.collectWhileActive(this)
        advanceUntilIdle()
        fake.error = { op -> if (op == "ManualImport.create") IOException("offline") else null }

        vm.selectAll()
        vm.importSelected()
        advanceUntilIdle()

        assertEquals(ManualImportNotice.QUEUED, vm.state.value.notice)
    }

    @Test
    fun `folder errors surface without clearing the previous items`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.manualImports += item(1)
        val vm = ManualImportViewModel(repository(testScheduler), downloadId = null, outputPath = null)
        vm.state.collectWhileActive(this)
        advanceUntilIdle()
        vm.loadFolderItems("/tv")
        advanceUntilIdle()
        assertEquals(1, vm.state.value.items.size)

        fake.error = { op -> if (op == "ManualImport.list") IOException("offline") else null }
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
