package dev.appolon.homelab.arr.feature.sonarr.settings.connect.vm

import dev.appolon.homelab.arr.api.sonarr.FakeSonarrConnect
import dev.appolon.homelab.arr.api.sonarr.FakeSonarrDiscovery
import dev.appolon.homelab.arr.api.sonarr.model.ImportListResource
import dev.appolon.homelab.arr.api.sonarr.model.ListSyncLevelType
import dev.appolon.homelab.arr.api.sonarr.model.MonitorTypes
import dev.appolon.homelab.arr.api.sonarr.model.SeriesTypes
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.FakeConnectActionQueue
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.FakeConnectCommandApi
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.FakeConnectEntityStore
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.FakeConnectMetadataApi
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.FakeConnectTagApi
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.data.SonarrConnectRepository
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
class SonarrImportListsViewModelTest {

    private lateinit var connect: FakeSonarrConnect
    private lateinit var discovery: FakeSonarrDiscovery
    private lateinit var tagApi: FakeConnectTagApi
    private lateinit var metadataApi: FakeConnectMetadataApi
    private lateinit var commandApi: FakeConnectCommandApi
    private lateinit var store: FakeConnectEntityStore
    private lateinit var queue: FakeConnectActionQueue

    @Before
    fun setUp() {
        connect = FakeSonarrConnect()
        discovery = FakeSonarrDiscovery()
        tagApi = FakeConnectTagApi()
        metadataApi = FakeConnectMetadataApi()
        commandApi = FakeConnectCommandApi()
        store = FakeConnectEntityStore()
        queue = FakeConnectActionQueue()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun repository(scheduler: TestCoroutineScheduler) = SonarrConnectRepository(
        serverId = ServerId("s1"),
        store = store,
        indexerApi = connect.indexerApi,
        indexerConfigApi = connect.indexerConfigApi,
        indexerFlagApi = connect.indexerFlagApi,
        downloadClientApi = connect.downloadClientApi,
        downloadClientConfigApi = connect.downloadClientConfigApi,
        notificationApi = connect.notificationApi,
        remotePathMappingApi = connect.remotePathMappingApi,
        importListApi = discovery.importList,
        importListConfigApi = discovery.importListConfigApi,
        importListExclusionApi = discovery.importListExclusion,
        metadataApi = metadataApi,
        commandApi = commandApi,
        tagApi = tagApi,
        actionQueue = queue,
        now = { 1_000L },
        ioContext = StandardTestDispatcher(scheduler),
    )

    @Test
    fun `sync preview summarizes lists exclusions and sync level`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        discovery.importListConfig =
            dev.appolon.homelab.arr.api.sonarr.model.ImportListConfigResource(
                listSyncLevel = ListSyncLevelType.KEEP_AND_TAG,
            )
        discovery.importListStore[1] = ImportListResource(
            id = 1,
            name = "Trakt Popular",
            enableAutomaticAdd = true,
            shouldMonitor = MonitorTypes.FUTURE,
            qualityProfileId = 3,
            rootFolderPath = "/tv",
        )
        discovery.importListExclusionStore[2] =
            dev.appolon.homelab.arr.api.sonarr.model.ImportListExclusionResource(
                id = 2,
                tvdbId = 99,
                title = "Old Show",
            )
        val vm = SonarrImportListsViewModel(ServerId("s1"), repository(testScheduler))
        advanceUntilIdle()

        val preview = vm.state.value.preview
        assertEquals(1, preview.rows.size)
        val row = preview.rows.single()
        assertEquals("Trakt Popular", row.name)
        assertTrue(row.automaticAdd)
        assertEquals("future", row.monitor)
        assertEquals(3, row.qualityProfileId)
        assertEquals("/tv", row.rootFolderPath)
        assertEquals(1, preview.exclusionCount)
        assertEquals(ListSyncLevelType.KEEP_AND_TAG, preview.syncLevel)
        Dispatchers.resetMain()
    }

    @Test
    fun `sync posts the server command and testall probes`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        discovery.importListStore[1] = ImportListResource(id = 1, name = "Trakt")
        val vm = SonarrImportListsViewModel(ServerId("s1"), repository(testScheduler))
        advanceUntilIdle()

        vm.sync()
        advanceUntilIdle()
        assertEquals(ImportListsNotice.SYNC_STARTED, vm.state.value.notice)
        assertEquals("ImportListSync", commandApi.started.single().name)

        vm.testAll()
        advanceUntilIdle()

        assertEquals(ImportListsNotice.TEST_ALL_DONE, vm.state.value.notice)
        assertEquals(1, discovery.testedAllCount.single())
        assertTrue(queue.pending().isEmpty())
        Dispatchers.resetMain()
    }

    @Test
    fun `list editor round-trips the add options`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        discovery.importListSchemas += ImportListResource(
            implementationName = "Trakt List",
            implementation = "TraktList",
        )
        val vm = SonarrImportListEditorViewModel(ServerId("s1"), null, repository(testScheduler))
        advanceUntilIdle()
        vm.pickSchema(vm.state.value.schemas.single())
        advanceUntilIdle()

        vm.updateDraft {
            it.copy(
                seriesType = SeriesTypes.ANIME,
                seasonFolder = false,
                qualityProfileId = 7,
                searchForMissingEpisodes = true,
            )
        }
        vm.save()
        advanceUntilIdle()

        val stored = discovery.importListStore.values.single()
        assertEquals(SeriesTypes.ANIME, stored.seriesType)
        assertEquals(false, stored.seasonFolder)
        assertEquals(7, stored.qualityProfileId)
        assertTrue(stored.searchForMissingEpisodes)
        assertEquals(ImportListEditorNotice.SAVED, vm.state.value.notice)
        Dispatchers.resetMain()
    }

    @Test
    fun `exclusions add and remove round-trip`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = SonarrImportListExclusionsViewModel(ServerId("s1"), repository(testScheduler))
        advanceUntilIdle()

        vm.startAdd()
        vm.updateDraft { it.copy(tvdbId = "72129", title = "Old Show") }
        vm.add()
        advanceUntilIdle()

        assertEquals(ExclusionsNotice.ADDED, vm.state.value.notice)
        assertEquals("Old Show", discovery.importListExclusionStore.values.single().title)

        val row = vm.state.value.exclusions.single()
        vm.requestDelete(row)
        vm.deleteConfirmed()
        advanceUntilIdle()

        assertEquals(ExclusionsNotice.DELETED, vm.state.value.notice)
        assertTrue(discovery.importListExclusionStore.isEmpty())
        Dispatchers.resetMain()
    }
}
