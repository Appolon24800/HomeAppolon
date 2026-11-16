package dev.appolon.homelab.arr.servers.vm

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.data.ServerRegistry
import dev.appolon.homelab.arr.data.registry.GlobalSettings
import dev.appolon.homelab.arr.net.TrustSpec
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProxySettingsViewModelTest {
    @Test
    fun savingGlobalSettingsKeepsTheCertificatePinOfADirectServer() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        var model: ProxySettingsViewModel? = null
        try {
            val directory = Files.createTempDirectory("proxy-settings-test").toFile()
            val settings = GlobalSettings(
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(backgroundScope.coroutineContext + dispatcher),
                    produceFile = { File(directory, "test.preferences_pb") },
                ),
            )
            val pin = TrustSpec.Pinned("a".repeat(64))
            val registry = Registry(
                ArrServer(ServerId("pinned"), AppType.RADARR, "Pinned", "https://example.test/", "key", trust = pin),
            )
            val vm = ProxySettingsViewModel(settings, registry)
            model = vm
            vm.state.first { it.loaded }

            assertFalse(vm.state.value.servers.single().override)
            assertEquals(pin, vm.state.value.servers.single().form.toTrustSpec())
            vm.save()
            vm.state.first { it.savedTick > 0 }

            assertEquals(pin, registry.servers().single().trust)
        } finally {
            model?.viewModelScope?.cancel()
            backgroundScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun invalidFingerprintWithoutAProxyDoesNotSaveOrRemoveTheExistingPin() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        var model: ProxySettingsViewModel? = null
        try {
            val directory = Files.createTempDirectory("proxy-settings-test").toFile()
            val settings = GlobalSettings(
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(backgroundScope.coroutineContext + dispatcher),
                    produceFile = { File(directory, "test.preferences_pb") },
                ),
            )
            val pin = TrustSpec.Pinned("b".repeat(64))
            val registry = Registry(
                ArrServer(ServerId("pinned"), AppType.RADARR, "Pinned", "https://example.test/", "key", trust = pin),
            )
            val vm = ProxySettingsViewModel(settings, registry)
            model = vm
            vm.state.first { it.loaded }
            val row = vm.state.value.servers.single()
            vm.updateServer(row.server.id, row.form.copy(fingerprint = "broken"))

            vm.save()

            assertTrue(vm.state.value.showErrors)
            assertEquals(0, vm.state.value.savedTick)
            assertEquals(pin, registry.servers().single().trust)
        } finally {
            model?.viewModelScope?.cancel()
            backgroundScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private class Registry(server: ArrServer) : ServerRegistry {
        private val rows = MutableStateFlow(listOf(server))
        override suspend fun servers() = rows.value
        override suspend fun serversOfType(type: AppType) = rows.value.filter { it.type == type }
        override suspend fun byId(id: ServerId) = rows.value.firstOrNull { it.id == id }
        override suspend fun upsert(server: ArrServer): ArrServer {
            rows.value = rows.value.filterNot { it.id == server.id } + server
            return server
        }
        override suspend fun delete(id: ServerId) {
            rows.value = rows.value.filterNot { it.id == id }
        }
        override suspend fun reorder(idsInOrder: List<ServerId>) = Unit
        override fun observe() = rows
    }
}
