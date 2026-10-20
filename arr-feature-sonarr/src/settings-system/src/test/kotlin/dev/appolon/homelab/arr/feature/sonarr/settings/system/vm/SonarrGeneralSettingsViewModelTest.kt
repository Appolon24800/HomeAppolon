package dev.appolon.homelab.arr.feature.sonarr.settings.system.vm

import dev.appolon.homelab.arr.api.sonarr.model.AuthenticationType
import dev.appolon.homelab.arr.api.sonarr.model.HostConfigResource
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SonarrGeneralSettingsViewModelTest : SonarrSettingsSystemVmTest() {

    @Test
    fun `loads the host config into the draft with a blank password`() = runTest {
        api.hostConfig = HostConfigResource(
            id = 1,
            port = 8989,
            urlBase = "/sonarr",
            authenticationMethod = AuthenticationType.FORMS,
            username = "admin",
            password = "server-secret",
        )

        val vm = withViewModel(::SonarrGeneralSettingsViewModel) { it.lce }
        val content = (vm.lce.value as LceState.Content).data

        assertEquals("/sonarr", content.draft.urlBase)
        assertEquals("admin", content.draft.username)
        assertEquals("", content.draft.password)
        assertFalse(content.dirty)
    }

    @Test
    fun `save applies the draft and keeps a blank password unchanged`() = runTest {
        api.hostConfig = HostConfigResource(id = 1, port = 8989)

        val vm = withViewModel(::SonarrGeneralSettingsViewModel) { it.lce }
        vm.edit { it.copy(port = "9110", analyticsEnabled = true) }
        vm.save()
        advanceUntilIdle()

        assertEquals(9110, api.hostConfig.port)
        assertTrue(api.hostConfig.analyticsEnabled)
        assertNull(api.hostConfig.password)
        assertTrue(vm.state.value.notice == GeneralSettingsNotice.SAVED)
    }

    @Test
    fun `save with a typed password forwards it but never caches it`() = runTest {
        api.hostConfig = HostConfigResource(id = 1, port = 8989, password = "old")

        val vm = withViewModel(::SonarrGeneralSettingsViewModel) { it.lce }
        vm.edit { it.copy(authenticationMethod = AuthenticationType.BASIC, password = "new-secret") }
        vm.save()
        advanceUntilIdle()

        assertEquals("new-secret", api.hostConfig.password)
        assertNull(vm.state.value.writeError)
        // The cache never holds the secret: a fresh ViewModel reading the
        // same store seeds a blank password from the stripped row.
        val second = withViewModel(::SonarrGeneralSettingsViewModel) { it.lce }
        assertEquals("", (second.lce.value as LceState.Content).data.draft.password)
    }

    @Test
    fun `invalid port blocks the save`() = runTest {
        api.hostConfig = HostConfigResource(id = 1, port = 8989)

        val vm = withViewModel(::SonarrGeneralSettingsViewModel) { it.lce }
        vm.edit { it.copy(port = "not-a-port") }
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.notice == null)
        assertTrue((vm.lce.value as LceState.Content).data.portError)
    }

    @Test
    fun `offline save queues and shows the queued notice`() = runTest {
        api.hostConfig = HostConfigResource(id = 1, port = 8989)
        api.error = { IOException("offline") }

        val vm = withViewModel(::SonarrGeneralSettingsViewModel) { it.lce }
        vm.edit { it.copy(port = "8321") }
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.notice == GeneralSettingsNotice.SAVED_QUEUED)
        assertEquals(1, queue.pending().size)
    }

    @Test
    fun `network failure on first load surfaces an error state`() = runTest {
        api.error = { IOException("offline") }

        val vm = withViewModel(::SonarrGeneralSettingsViewModel) { it.lce }

        assertTrue(vm.lce.value is LceState.Error)
    }
}
