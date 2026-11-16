package dev.appolon.homelab.arr.servers.vm

import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.data.ServerRegistry
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
import dev.appolon.homelab.arr.servers.logic.ServerForm
import dev.appolon.homelab.arr.servers.net.ConnectionOutcome
import dev.appolon.homelab.arr.servers.net.ConnectionTester
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerEditViewModelTest {
    private val main = StandardTestDispatcher()
    private val registry = RecordingRegistry()
    private val secrets = object : ArrSecrets {
        override fun encrypt(plaintext: String) = "encrypted:$plaintext"
        override fun decrypt(envelope: String) = envelope.removePrefix("encrypted:")
    }

    @Before fun setUp() = Dispatchers.setMain(main)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(tester: ConnectionTester = ConnectionTester { _, _ ->
        ConnectionOutcome.Success("1.0")
    }) = ServerEditViewModel(registry, secrets, tester, null).also { vm ->
        vm.update {
            ServerForm(name = "Radarr", baseUrl = "http://example.test/radarr", apiKey = "key")
        }
    }

    @Test
    fun repeatedSaveTapsCreateOneServer() = runTest(main) {
        val vm = viewModel()
        vm.save()
        vm.save()
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()

        assertEquals(1, registry.saved.size)
        assertEquals("http://example.test/radarr/", registry.saved.single().baseUrl)
        assertEquals("encrypted:key", registry.saved.single().apiKeyEncrypted)
    }

    @Test
    fun editingCredentialsCancelsThePreviousConnectionTest() = runTest(main) {
        val response = CompletableDeferred<ConnectionOutcome>()
        var tests = 0
        val vm = viewModel(ConnectionTester { _, _ ->
            tests++
            response.await()
        })
        vm.testConnection()
        vm.testConnection()
        runCurrent()
        assertEquals(1, tests)

        vm.update { it.copy(apiKey = "changed-key") }
        response.complete(ConnectionOutcome.Success("old-server"))
        advanceUntilIdle()

        assertFalse(vm.state.value.testing)
        assertEquals(ServerEditViewModel.TestState.Idle, vm.state.value.test)
        assertEquals("changed-key", vm.state.value.form.apiKey)
    }

    @Test
    fun editingAFormInvalidatesItsSuccessfulConnectionTest() = runTest(main) {
        val vm = viewModel()
        vm.testConnection()
        advanceUntilIdle()
        assertEquals(ServerEditViewModel.TestState.Succeeded("1.0"), vm.state.value.test)

        vm.update { it.copy(baseUrl = "http://elsewhere.test/") }

        assertEquals(ServerEditViewModel.TestState.Idle, vm.state.value.test)
    }

    private class RecordingRegistry : ServerRegistry {
        val saved = mutableListOf<ArrServer>()
        override suspend fun upsert(server: ArrServer): ArrServer = server.also { saved += it }
        override suspend fun servers(): List<ArrServer> = saved.toList()
        override suspend fun serversOfType(type: AppType) = saved.filter { it.type == type }
        override suspend fun byId(id: ServerId) = saved.firstOrNull { it.id == id }
        override suspend fun delete(id: ServerId) { saved.removeAll { it.id == id } }
        override suspend fun reorder(idsInOrder: List<ServerId>) = Unit
        override fun observe() = MutableStateFlow(saved.toList())
    }
}
