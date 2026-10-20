package dev.appolon.homelab.arr.servers.vm

import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.data.ServerRegistry
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
import dev.appolon.homelab.arr.net.ServerEndpoint
import dev.appolon.homelab.arr.servers.net.ConnectionOutcome
import dev.appolon.homelab.arr.servers.net.ConnectionTester
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerListViewModelTest {

    private val main = UnconfinedTestDispatcher()

    private class FakeRegistry(initial: List<ArrServer>) : ServerRegistry {
        val flow = MutableStateFlow(initial)

        val reorderCalls = mutableListOf<List<ServerId>>()

        override suspend fun servers(): List<ArrServer> = flow.value

        override suspend fun serversOfType(type: AppType): List<ArrServer> =
            flow.value.filter { it.type == type }

        override suspend fun byId(id: ServerId): ArrServer? =
            flow.value.firstOrNull { it.id == id }

        override suspend fun upsert(server: ArrServer): ArrServer {
            flow.value = flow.value.filterNot { it.id == server.id } + server
            return server
        }

        override suspend fun delete(id: ServerId) {
            flow.value = flow.value.filterNot { it.id == id }
        }

        override suspend fun reorder(idsInOrder: List<ServerId>) {
            reorderCalls += idsInOrder
            val byId = flow.value.associateBy { it.id }
            flow.value = idsInOrder.mapNotNull { byId[it] } +
                flow.value.filter { it.id !in idsInOrder.toSet() }
        }

        override fun observe() = flow
    }

    private object FakeSecrets : ArrSecrets {
        override fun encrypt(plaintext: String): String = "env:$plaintext"

        override fun decrypt(envelope: String): String = envelope.removePrefix("env:")
    }

    private val tester = ConnectionTester { endpoint, _ ->
        if (endpoint.baseUrl.startsWith("http://up")) {
            ConnectionOutcome.Success("1.0")
        } else {
            ConnectionOutcome.Failure(ArrError.Network(null))
        }
    }

    private fun server(id: String, order: Int, baseUrl: String) = ArrServer(
        id = ServerId(id),
        type = AppType.RADARR,
        name = "Server $id",
        baseUrl = baseUrl,
        apiKeyEncrypted = "env:key-$id",
        order = order,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `empty registry renders the empty state`() = runTest {
        val vm = ServerListViewModel(FakeRegistry(emptyList()), FakeSecrets, tester)
        val job = launch { vm.lce.collect {} }
        advanceUntilIdle()
        assertEquals(LceState.Empty, vm.lce.value)
        job.cancel()
    }

    @Test
    fun `servers render as content with probed health`() = runTest {
        val registry = FakeRegistry(
            listOf(
                server("a", 1, "http://up.example"),
                server("b", 2, "http://down.example"),
            ),
        )
        val vm = ServerListViewModel(registry, FakeSecrets, tester)
        val job = launch { vm.lce.collect {} }
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(listOf("a", "b"), state.servers.map { it.id.value })
        assertEquals(ServerListViewModel.Health.OK, state.health[ServerId("a")])
        assertEquals(ServerListViewModel.Health.UNREACHABLE, state.health[ServerId("b")])
        job.cancel()
    }

    @Test
    fun `moving up swaps the order through the registry`() = runTest {
        val registry = FakeRegistry(
            listOf(
                server("a", 1, "http://up.example"),
                server("b", 2, "http://up.example"),
            ),
        )
        val vm = ServerListViewModel(registry, FakeSecrets, tester)
        val job = launch { vm.lce.collect {} }
        advanceUntilIdle()

        vm.moveUp(ServerId("b"))
        advanceUntilIdle()

        assertEquals(listOf("b", "a"), registry.reorderCalls.single().map { it.value })
        job.cancel()
    }

    @Test
    fun `moving the first server up is a no-op`() = runTest {
        val registry = FakeRegistry(listOf(server("a", 1, "http://up.example")))
        val vm = ServerListViewModel(registry, FakeSecrets, tester)
        val job = launch { vm.lce.collect {} }
        advanceUntilIdle()

        vm.moveUp(ServerId("a"))
        advanceUntilIdle()

        assertEquals(0, registry.reorderCalls.size)
        job.cancel()
    }

    @Test
    fun `delete goes through the whole-graph remover`() = runTest {
        val registry = FakeRegistry(listOf(server("a", 1, "http://up.example")))
        val deleted = mutableListOf<ServerId>()
        val vm = ServerListViewModel(registry, FakeSecrets, tester) { deleted += it }
        val job = launch { vm.lce.collect {} }
        advanceUntilIdle()

        vm.delete(ServerId("a"))
        advanceUntilIdle()

        assertEquals(listOf(ServerId("a")), deleted)
        job.cancel()
    }
}
