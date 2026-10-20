package dev.appolon.homelab.arr.feature.radarr.settings.connect.vm

import dev.appolon.homelab.arr.api.radarr.FakeRadarrConnect
import dev.appolon.homelab.arr.api.radarr.model.IndexerResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.settings.connect.FakeConnectActionQueue
import dev.appolon.homelab.arr.feature.radarr.settings.connect.FakeConnectCommandApi
import dev.appolon.homelab.arr.feature.radarr.settings.connect.FakeConnectEntityStore
import dev.appolon.homelab.arr.feature.radarr.settings.connect.FakeConnectMetadataApi
import dev.appolon.homelab.arr.feature.radarr.settings.connect.FakeConnectTagApi
import dev.appolon.homelab.arr.api.radarr.FakeRadarrDiscovery
import dev.appolon.homelab.arr.feature.radarr.settings.connect.data.RadarrConnectRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RadarrIndexersViewModelTest {

    private lateinit var api: FakeRadarrConnect
    private lateinit var store: FakeConnectEntityStore
    private lateinit var queue: FakeConnectActionQueue

    @Before
    fun setUp() {
        api = FakeRadarrConnect()
        store = FakeConnectEntityStore()
        queue = FakeConnectActionQueue()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun repository(scheduler: TestCoroutineScheduler) = RadarrConnectRepository(
        serverId = ServerId("s1"),
        store = store,
        indexerApi = api,
        indexerConfigApi = api,
        indexerFlagApi = api,
        downloadClientApi = api,
        downloadClientConfigApi = api,
        notificationApi = api,
        remotePathMappingApi = api,
        importListApi = FakeRadarrDiscovery(),
        importListConfigApi = FakeRadarrDiscovery(),
        importListExclusionApi = FakeRadarrDiscovery(),
        importListMoviesApi = FakeRadarrDiscovery(),
        metadataApi = FakeConnectMetadataApi(),
        commandApi = FakeConnectCommandApi(),
        tagApi = FakeConnectTagApi(),
        actionQueue = queue,
        ioContext = StandardTestDispatcher(scheduler),
    )

    @Test
    fun `loads indexers cache-first`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.indexers += IndexerResource(id = 1, name = "Prowlarr", priority = 10)
        val vm = RadarrIndexersViewModel(ServerId("s1"), repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<RadarrIndexersContent>).data
        assertEquals(listOf("Prowlarr"), content.indexers.map { it.name })
    }

    @Test
    fun `test-all keeps per-indexer verdicts`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.indexers += IndexerResource(id = 3, name = "A", enableRss = true)
        val vm = RadarrIndexersViewModel(ServerId("s1"), repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.testAll()
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<RadarrIndexersContent>).data
        assertNotNull(content.testResults[3])
        assertTrue(content.testResults.getValue(3).isValid)
        assertEquals(IndexersNotice.TEST_ALL_DONE, vm.state.value.notice)
    }

    @Test
    fun `delete confirms then removes`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.indexers += IndexerResource(id = 1, name = "Gone")
        val vm = RadarrIndexersViewModel(ServerId("s1"), repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.requestDelete(api.indexers.first())
        vm.deleteConfirmed()
        advanceUntilIdle()

        assertEquals(IndexersNotice.DELETED, vm.state.value.notice)
        assertTrue(api.indexers.isEmpty())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> StateFlow<T>.collectWhileActive(scope: TestScope) {
    scope.backgroundScope.launch { this@collectWhileActive.collect { } }
}
