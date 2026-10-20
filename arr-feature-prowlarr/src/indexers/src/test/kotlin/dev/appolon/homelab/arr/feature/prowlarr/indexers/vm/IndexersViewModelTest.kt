package dev.appolon.homelab.arr.feature.prowlarr.indexers.vm

import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.prowlarr.FakeProwlarrIndexers
import dev.appolon.homelab.arr.api.prowlarr.model.AppProfileResource
import dev.appolon.homelab.arr.api.prowlarr.model.ApplyTags
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerResource
import dev.appolon.homelab.arr.api.prowlarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.prowlarr.indexers.FakeActionQueue
import dev.appolon.homelab.arr.feature.prowlarr.indexers.FakeEntityStore
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.ProwlarrIndexersRepository
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.ProtocolFilter
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.StateFilter
import dev.appolon.homelab.arr.feature.prowlarr.indexers.fakeIndexersApis
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
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
class IndexersViewModelTest {

    private val serverId = ServerId("s1")
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var fake: FakeProwlarrIndexers
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeActionQueue
    private lateinit var repository: ProwlarrIndexersRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        fake = FakeProwlarrIndexers()
        store = FakeEntityStore()
        queue = FakeActionQueue()
        repository = ProwlarrIndexersRepository(
            serverId = serverId,
            store = store,
            apis = fakeIndexersApis(fake),
            actionQueue = queue,
            now = { 1_000L },
            io = dispatcher,
        )
        listOf(
            IndexerResource(id = 1, name = "Alpha", enable = true, priority = 25),
            IndexerResource(id = 2, name = "Beta", enable = false, priority = 50),
        ).forEach { fake.indexers[it.id] = it }
        fake.tags[10] = TagResource(id = 10, label = "hd")
        fake.appProfiles[1] = AppProfileResource(id = 1, name = "Standard")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun runVm(block: suspend (IndexersViewModel) -> Unit) = runTest(dispatcher) {
        val vm = IndexersViewModel(repository)
        backgroundScope.launch { vm.lce.collect {} }
        block(vm)
        vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `init loads indexers and exposes content`() = runVm { vm ->
        assertEquals(listOf("Alpha", "Beta"), vm.state.value.indexers.map { it.name })
        assertTrue(vm.state.value.loaded)
        assertTrue(vm.lce.value is LceState.Content)
    }

    @Test
    fun `protocol filter narrows the presentation`() = runVm { vm ->
        vm.setProtocolFilter(ProtocolFilter.ALL)
        vm.setStateFilter(StateFilter.DISABLED)
        assertEquals(listOf("Beta"), vm.state.value.presentation.map { it.name })
        vm.clearFilters()
        assertEquals(2, vm.state.value.presentation.size)
    }

    @Test
    fun `search narrows the presentation`() = runVm { vm ->
        vm.setSearch("alp")
        assertEquals(listOf("Alpha"), vm.state.value.presentation.map { it.name })
    }

    @Test
    fun `selection mode targets only the selection`() = runVm { vm ->
        vm.enterSelectMode(2)
        vm.toggleSelected(1)
        assertEquals(setOf(1, 2), vm.state.value.selection)
        vm.selectAllPresented()
        assertEquals(setOf(1, 2), vm.state.value.bulkTargets.toSet())
        vm.exitSelectMode()
        assertEquals(listOf(1, 2), vm.state.value.bulkTargets)
    }

    @Test
    fun `setEnabled offline queues and surfaces the queued message`() = runVm { vm ->
        fake.failure = { call -> if (call.operation == "updateIndexer") IOException("off") else null }
        vm.setEnabled(2, true)
        assertEquals(IndexersMessage.ToggleQueued, vm.state.value.message)
        assertEquals(setOf(2), vm.state.value.queuedIndexerIds)
        vm.consumeMessage()
        assertEquals(null, vm.state.value.message)
    }

    @Test
    fun `bulk editor submits enable and tags with ApplyTags semantics`() = runVm { vm ->
        vm.openBulkEditor()
        vm.updateBulkEditor { it.copy(enable = true, tagLabels = listOf("hd")) }
        vm.submitBulkEditor()
        val call = fake.calls.first { it.operation == "updateIndexers" }
        val bulk = call.arguments["resource"] as dev.appolon.homelab.arr.api.prowlarr.model.IndexerBulkResource
        assertEquals(listOf(1, 2), bulk.ids)
        assertEquals(true, bulk.enable)
        assertEquals(listOf(10), bulk.tags)
        assertEquals(ApplyTags.ADD, bulk.applyTags)
        assertEquals(IndexersMessage.BulkSynced, vm.state.value.message)
    }

    @Test
    fun `confirm delete with a selection removes only the selection`() = runVm { vm ->
        vm.enterSelectMode(1)
        vm.requestDelete()
        vm.confirmDelete()
        assertTrue(fake.calls.any { it.operation == "deleteIndexer" })
        assertEquals(listOf("Beta"), vm.state.value.indexers.map { it.name })
        assertEquals(IndexersMessage.DeleteSynced, vm.state.value.message)
    }

    @Test
    fun `test single and test all surface probe results`() = runVm { vm ->
        vm.testIndexer(1)
        assertEquals(IndexersMessage.TestPassed, vm.state.value.message)
        vm.consumeMessage()
        vm.testAll()
        assertEquals(IndexersMessage.TestAllStarted, vm.state.value.message)
        assertTrue(fake.calls.any { it.operation == "testAllIndexers" })
    }

    @Test
    fun `failed probes surface the error without queueing`() = runVm { vm ->
        fake.errorStatus = { call -> if (call.operation == "testIndexer") 400 else null }
        vm.testIndexer(1)
        assertTrue(vm.state.value.message is IndexersMessage.TestFailed)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `tag labels and profile labels resolve against caches`() = runVm { vm ->
        val state = vm.state.value
        val alpha = state.indexers.first { it.id == 1 }
        assertEquals("Standard", state.appProfileLabel(1))
        assertEquals(listOf("hd"), state.tagLabels(alpha.copy(tags = listOf(10))))
    }
}
