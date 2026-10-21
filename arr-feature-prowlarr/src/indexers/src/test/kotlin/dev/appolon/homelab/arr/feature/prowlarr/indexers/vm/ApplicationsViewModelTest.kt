package dev.appolon.homelab.arr.feature.prowlarr.indexers.vm

import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.prowlarr.FakeProwlarrIndexers
import dev.appolon.homelab.arr.api.prowlarr.model.AppProfileResource
import dev.appolon.homelab.arr.api.prowlarr.model.ApplicationResource
import dev.appolon.homelab.arr.api.prowlarr.model.ApplicationSyncLevel
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.prowlarr.indexers.FakeActionQueue
import dev.appolon.homelab.arr.feature.prowlarr.indexers.FakeEntityStore
import dev.appolon.homelab.arr.feature.prowlarr.indexers.ProwlarrCommandRunner
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.ProwlarrIndexersRepository
import dev.appolon.homelab.arr.feature.prowlarr.indexers.fakeIndexersApis
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ApplicationsViewModelTest {

    private val serverId = ServerId("s1")
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var fake: FakeProwlarrIndexers
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeActionQueue
    private lateinit var repository: ProwlarrIndexersRepository
    private var syncResult: ArrResult<Unit> = ArrResult.ok(Unit)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        fake = FakeProwlarrIndexers()
        store = FakeEntityStore()
        queue = FakeActionQueue()
        repository = ProwlarrIndexersRepository(
            serverId = serverId,
            store = store,
            apis = fakeIndexersApis(
                fake,
                commandRunner = ProwlarrCommandRunner { syncResult },
            ),
            actionQueue = queue,
            now = { 1_000L },
            io = dispatcher,
        )
        fake.applications[3] = ApplicationResource(
            id = 3,
            name = "Radarr",
            implementationName = "Radarr",
            syncLevel = ApplicationSyncLevel.FULL_SYNC,
        )
        fake.appProfiles[1] = AppProfileResource(id = 1, name = "Standard", minimumSeeders = 1)
        listOf(
            IndexerResource(id = 10, name = "Alpha", enable = true, appProfileId = 1),
            IndexerResource(id = 11, name = "Beta", enable = false, appProfileId = 0),
        ).forEach { fake.indexers[it.id] = it }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun runVm(block: suspend (ApplicationsViewModel) -> Unit) = runTest(dispatcher) {
        val vm = ApplicationsViewModel(repository)
        block(vm)
        vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `init loads applications`() = runVm { vm ->
        assertEquals(listOf("Radarr"), vm.state.value.applications.map { it.name })
    }

    @Test
    fun `sync all reports started when the command lands`() = runVm { vm ->
        vm.syncAll()
        assertEquals(ApplicationsMessage.SyncStarted, vm.state.value.message)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `sync all queues when offline`() = runVm { vm ->
        syncResult = ArrResult.err(ArrError.Network(IOException("down")))
        vm.syncAll()
        assertEquals(ApplicationsMessage.SyncQueued, vm.state.value.message)
        assertEquals("api/v1/command", queue.pending().single().path)
    }

    @Test
    fun `sync preview lists indexers with their app profile labels`() = runVm { vm ->
        vm.openSyncPreview(vm.state.value.applications.first())
        val state = vm.state.value
        assertEquals(2, state.syncPreview.size)
        assertEquals("Alpha", state.syncPreview.first().indexerName)
        assertEquals("Standard", state.syncPreview.first().appProfileLabel)
        vm.closeSyncPreview()
        assertTrue(vm.state.value.syncPreview.isEmpty())
    }

    @Test
    fun `editing an application persists the sync level`() = runVm { vm ->
        vm.edit(vm.state.value.applications.first())
        vm.setSyncLevel(ApplicationSyncLevel.ADD_ONLY)
        vm.setName("Radarr 2")
        vm.save()
        val updated = fake.applications.getValue(3)
        assertEquals(ApplicationSyncLevel.ADD_ONLY, updated.syncLevel)
        assertEquals("Radarr 2", updated.name)
        assertEquals(ApplicationsMessage.Saved, vm.state.value.message)
    }

    @Test
    fun `app profile editor creates profiles and refreshes the list`() = runVm { vm ->
        vm.openProfileEditor(null)
        vm.setProfileName("Music")
        vm.setProfileMinimumSeeders(3)
        vm.setProfileEnableRss(false)
        vm.saveProfile()
        val created = fake.appProfiles.values.first { it.name == "Music" }
        assertEquals(3, created.minimumSeeders)
        assertEquals(false, created.enableRss)
        assertEquals(
            "Music",
            vm.state.value.appProfiles.first { it.id == created.id }.name,
        )
    }

    @Test
    fun `app profile delete confirms then removes`() = runVm { vm ->
        vm.requestProfileDelete(1)
        vm.confirmProfileDelete()
        assertTrue(fake.appProfiles.isEmpty())
        assertEquals(ApplicationsMessage.ProfileDeleted, vm.state.value.message)
    }
}
