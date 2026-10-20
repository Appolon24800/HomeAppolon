package dev.appolon.homelab.arr.feature.prowlarr.indexers.vm

import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.prowlarr.FakeProwlarrIndexers
import dev.appolon.homelab.arr.api.prowlarr.model.Field
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerProxyResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.prowlarr.indexers.FakeActionQueue
import dev.appolon.homelab.arr.feature.prowlarr.indexers.FakeEntityStore
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.FieldForms
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
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProxiesViewModelTest {

    private val serverId = ServerId("s1")
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var fake: FakeProwlarrIndexers
    private lateinit var queue: FakeActionQueue
    private lateinit var repository: ProwlarrIndexersRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        fake = FakeProwlarrIndexers()
        queue = FakeActionQueue()
        repository = ProwlarrIndexersRepository(
            serverId = serverId,
            store = FakeEntityStore(),
            apis = fakeIndexersApis(fake),
            actionQueue = queue,
            now = { 1_000L },
            io = dispatcher,
        )
        fake.indexerProxies[5] = IndexerProxyResource(
            id = 5,
            name = "Socks",
            implementationName = "Socks5",
            fields = listOf(
                Field(name = "host", label = "Host", value = JsonPrimitive("127.0.0.1")),
                Field(name = "port", label = "Port", type = "number", value = JsonPrimitive(1080)),
            ),
        )
        fake.indexerProxySchemas += IndexerProxyResource(
            implementation = "Socks5",
            name = "Socks5",
            fields = listOf(
                Field(name = "host", label = "Host"),
                Field(name = "port", label = "Port", type = "number"),
            ),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun runVm(block: suspend (ProxiesViewModel) -> Unit) = runTest(dispatcher) {
        val vm = ProxiesViewModel(repository)
        block(vm)
        vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun `init loads proxies`() = runVm { vm ->
        assertEquals(listOf("Socks"), vm.state.value.proxies.map { it.name })
        assertTrue(vm.state.value.loaded)
    }

    @Test
    fun `edit seeds the form and saves field values`() = runVm { vm ->
        vm.edit(vm.state.value.proxies.first())
        vm.setName("Socks Primary")
        vm.setFieldValue("port", JsonPrimitive(1081))
        vm.save()
        val saved = fake.indexerProxies.getValue(5)
        assertEquals("Socks Primary", saved.name)
        assertEquals(1081, FieldForms.intOf(saved.fields?.first { it.name == "port" }?.value))
        assertEquals(ProxiesMessage.Saved, vm.state.value.message)
    }

    @Test
    fun `add opens on the schema`() = runVm { vm ->
        vm.openAdd()
        val form = vm.state.value.editor
        assertEquals(null, form?.editing)
        assertEquals("Socks5", form?.schema?.implementation)
        vm.setName("New Socks")
        vm.save()
        assertTrue(fake.indexerProxies.values.any { it.name == "New Socks" })
    }

    @Test
    fun `blank names report NameMissing`() = runVm { vm ->
        vm.openAdd()
        vm.save()
        assertEquals(ProxiesMessage.NameMissing, vm.state.value.message)
        assertTrue(fake.calls.none { it.operation == "createIndexerProxy" })
    }

    @Test
    fun `delete confirms and removes`() = runVm { vm ->
        vm.requestDelete(5)
        vm.confirmDelete()
        assertTrue(fake.indexerProxies.isEmpty())
        assertEquals(ProxiesMessage.Deleted, vm.state.value.message)
    }

    @Test
    fun `offline saves queue`() = runVm { vm ->
        vm.edit(vm.state.value.proxies.first())
        vm.setName("Offline Edit")
        fake.failure = { call -> if (call.operation == "updateIndexerProxy") IOException("x") else null }
        vm.save()
        assertEquals(ProxiesMessage.SavedQueued, vm.state.value.message)
        assertEquals("api/v1/indexerproxy/5", queue.pending().single().path)
    }

    @Test
    fun `test probes the built proxy`() = runVm { vm ->
        vm.edit(vm.state.value.proxies.first())
        vm.test()
        assertEquals(ProxiesMessage.TestPassed, vm.state.value.message)
        assertTrue(fake.calls.any { it.operation == "testIndexerProxy" })
    }
}
