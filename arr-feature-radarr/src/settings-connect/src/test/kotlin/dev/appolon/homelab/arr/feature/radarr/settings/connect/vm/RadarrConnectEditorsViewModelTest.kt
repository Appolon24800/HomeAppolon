package dev.appolon.homelab.arr.feature.radarr.settings.connect.vm

import dev.appolon.homelab.arr.api.radarr.FakeRadarrConnect
import dev.appolon.homelab.arr.api.radarr.FakeRadarrDiscovery
import dev.appolon.homelab.arr.api.radarr.model.ImportListResource
import dev.appolon.homelab.arr.api.radarr.model.NotificationResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.settings.connect.FakeConnectActionQueue
import dev.appolon.homelab.arr.feature.radarr.settings.connect.FakeConnectCommandApi
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.feature.radarr.settings.connect.FakeConnectMetadataApi
import dev.appolon.homelab.arr.feature.radarr.settings.connect.FakeConnectTagApi
import dev.appolon.homelab.arr.feature.radarr.settings.connect.data.RadarrConnectRepository
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
class RadarrConnectEditorsViewModelTest {

    private lateinit var connect: FakeRadarrConnect
    private lateinit var discovery: FakeRadarrDiscovery
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeConnectActionQueue

    @Before
    fun setUp() {
        connect = FakeRadarrConnect()
        discovery = FakeRadarrDiscovery()
        store = FakeEntityStore()
        queue = FakeConnectActionQueue()
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
        commandApi = FakeConnectCommandApi(),
        tagApi = FakeConnectTagApi(),
        actionQueue = queue,
        ioContext = StandardTestDispatcher(scheduler),
    )

    @Test
    fun `notification editor keeps only supported events in the matrix`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        connect.notifications += NotificationResource(
            id = 1,
            name = "Discord",
            onGrab = true,
            supportsOnGrab = true,
            supportsOnUpgrade = true,
        )
        val vm = RadarrNotificationEditorViewModel(
            ServerId("s1"),
            1,
            repository(testScheduler),
        )
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val draft = vm.state.value.draft
        assertEquals(setOf("onGrab", "onUpgrade"), draft?.events?.map { it.key }?.toSet())

        vm.toggleEvent("onUpgrade")
        vm.save()
        advanceUntilIdle()

        assertEquals(NotificationEditorNotice.SAVED, vm.state.value.notice)
        val saved = connect.notifications.single()
        assertTrue(saved.onUpgrade)
        assertTrue(saved.onGrab)
    }

    @Test
    fun `notification editor test probes the current draft`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        connect.notifications += NotificationResource(
            id = 1,
            name = "Discord",
            supportsOnGrab = true,
        )
        val vm = RadarrNotificationEditorViewModel(
            ServerId("s1"),
            1,
            repository(testScheduler),
        )
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.test()
        advanceUntilIdle()

        assertEquals(NotificationEditorNotice.TEST_PASSED, vm.state.value.notice)
        assertEquals("Discord", connect.testedNotifications.single().name)
    }

    @Test
    fun `indexer editor picks a schema and saves with tags`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        connect.indexerSchemas += dev.appolon.homelab.arr.api.radarr.model.IndexerResource(
            implementation = "Newznab",
            implementationName = "Newznab",
            supportsRss = true,
            supportsSearch = true,
        )
        val repository = repository(testScheduler)
        val vm = RadarrIndexerEditorViewModel(ServerId("s1"), null, repository)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.picker)
        vm.pickSchema(state.schemas.single())
        vm.updateDraft { it.copy(name = "NZB", tagLabels = listOf("usenet")) }
        vm.save()
        advanceUntilIdle()

        assertEquals(IndexerEditorNotice.SAVED, vm.state.value.notice)
        val saved = connect.indexers.single()
        assertEquals("NZB", saved.name)
        assertEquals(setOf(1), saved.tags)
    }

    @Test
    fun `import list editor saves add options`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        discovery.importListSchemas += ImportListResource(
            implementation = "TraktList",
            implementationName = "Trakt List",
        )
        val vm = RadarrImportListEditorViewModel(
            ServerId("s1"),
            null,
            repository(testScheduler),
        )
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.pickSchema(vm.state.value.schemas.single())
        vm.updateDraft { it.copy(name = "Popular", rootFolderPath = "/movies", searchOnAdd = true) }
        vm.save()
        advanceUntilIdle()

        assertEquals(ImportListEditorNotice.SAVED, vm.state.value.notice)
        val saved = discovery.importLists.single()
        assertEquals("/movies", saved.rootFolderPath)
        assertTrue(saved.searchOnAdd)
    }
}
