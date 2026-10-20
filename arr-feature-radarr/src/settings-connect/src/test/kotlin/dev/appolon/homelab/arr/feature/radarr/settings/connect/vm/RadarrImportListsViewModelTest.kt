package dev.appolon.homelab.arr.feature.radarr.settings.connect.vm

import dev.appolon.homelab.arr.api.radarr.FakeRadarrConnect
import dev.appolon.homelab.arr.api.radarr.FakeRadarrDiscovery
import dev.appolon.homelab.arr.api.radarr.model.ImportListMoviesResource
import dev.appolon.homelab.arr.api.radarr.model.ImportListResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.settings.connect.FakeConnectActionQueue
import dev.appolon.homelab.arr.feature.radarr.settings.connect.FakeConnectCommandApi
import dev.appolon.homelab.arr.feature.radarr.settings.connect.FakeConnectEntityStore
import dev.appolon.homelab.arr.feature.radarr.settings.connect.FakeConnectMetadataApi
import dev.appolon.homelab.arr.feature.radarr.settings.connect.FakeConnectTagApi
import dev.appolon.homelab.arr.feature.radarr.settings.connect.data.RadarrConnectRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RadarrImportListsViewModelTest {

    private lateinit var connect: FakeRadarrConnect
    private lateinit var discovery: FakeRadarrDiscovery
    private lateinit var commandApi: FakeConnectCommandApi
    private lateinit var store: FakeConnectEntityStore

    @Before
    fun setUp() {
        connect = FakeRadarrConnect()
        discovery = FakeRadarrDiscovery()
        commandApi = FakeConnectCommandApi()
        store = FakeConnectEntityStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun repository(scheduler: TestCoroutineScheduler) = RadarrConnectRepository(
        serverId = ServerId("s1"),
        store = store,
        indexerApi = connect,
        indexerConfigApi = connect,
        indexerFlagApi = connect,
        downloadClientApi = connect,
        downloadClientConfigApi = connect,
        notificationApi = connect,
        remotePathMappingApi = connect,
        importListApi = discovery,
        importListConfigApi = discovery,
        importListExclusionApi = discovery,
        importListMoviesApi = discovery,
        metadataApi = FakeConnectMetadataApi(),
        commandApi = commandApi,
        tagApi = FakeConnectTagApi(),
        actionQueue = FakeConnectActionQueue(),
        ioContext = StandardTestDispatcher(scheduler),
    )

    @Test
    fun `loads lists cache-first`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        discovery.importLists += ImportListResource(id = 1, name = "Trakt Popular")
        val vm = RadarrImportListsViewModel(ServerId("s1"), repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<RadarrImportListsContent>).data
        assertEquals(listOf("Trakt Popular"), content.lists.map { it.name })
    }

    @Test
    fun `sync posts the command then loads the preview`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        discovery.importLists += ImportListResource(id = 1, name = "Trakt Popular", enabled = true)
        discovery.discoveredMovies += ImportListMoviesResource(
            id = 1,
            title = "Fresh",
            isExisting = false,
            isExcluded = false,
        )
        val vm = RadarrImportListsViewModel(ServerId("s1"), repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.sync()
        advanceUntilIdle()

        assertEquals(ImportListsNotice.SYNC_STARTED, vm.state.value.notice)
        assertEquals("ImportListSync", commandApi.started.single().name)
        vm.openPreview()
        advanceUntilIdle()

        val preview = vm.state.value.preview
        assertEquals(1, preview?.addableCount)
        assertEquals("Fresh", preview?.movies?.single()?.title)
    }

    @Test
    fun `preview sheet counts existing and excluded rows`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        discovery.discoveredMovies += ImportListMoviesResource(id = 1, title = "A", isExisting = true)
        discovery.discoveredMovies += ImportListMoviesResource(id = 2, title = "B", isExcluded = true)
        discovery.discoveredMovies += ImportListMoviesResource(id = 3, title = "C")
        val vm = RadarrImportListsViewModel(ServerId("s1"), repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.openPreview()
        advanceUntilIdle()

        val preview = vm.state.value.preview
        assertEquals(1, preview?.addableCount)
        assertEquals(1, preview?.existingCount)
        assertEquals(1, preview?.excludedCount)
        assertTrue(preview?.error == null)
    }
}
