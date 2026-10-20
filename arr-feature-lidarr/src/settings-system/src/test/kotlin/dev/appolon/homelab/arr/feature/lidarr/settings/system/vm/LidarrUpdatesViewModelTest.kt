package dev.appolon.homelab.arr.feature.lidarr.settings.system.vm

import dev.appolon.homelab.arr.api.lidarr.model.LogFileResource
import dev.appolon.homelab.arr.api.lidarr.model.SystemResource
import dev.appolon.homelab.arr.api.lidarr.model.UpdateResource
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LidarrUpdatesViewModelTest : LidarrSettingsSystemVmTest() {

    private fun seed() {
        api.systemStatus = SystemResource(
            appName = "Lidarr",
            version = "2.9.5",
            branch = "main",
        )
        api.updates += UpdateResource(id = 1, version = "2.9.5.4400", installed = true, latest = false)
        api.updates += UpdateResource(
            id = 2,
            version = "2.9.6.4557",
            installed = false,
            latest = true,
            installable = true,
        )
        api.updateLogFiles += LogFileResource(id = 1, filename = "lidarr.update.txt")
    }

    @Test
    fun `loads updates with the installed row marked current`() = runTest {
        seed()
        val vm = withViewModel(::LidarrUpdatesViewModel) { it.lce }
        val content = (vm.lce.value as LceState.Content).data

        assertEquals("2.9.5", content.status!!.version)
        val current = content.updates.first { it.isCurrent }
        assertEquals("2.9.5.4400", current.update.version)
        val installable = content.updates.first { it.installable }
        assertEquals("2.9.6.4557", installable.update.version)
    }

    @Test
    fun `check bypasses the TTL and reports the check`() = runTest {
        seed()
        val vm = withViewModel(::LidarrUpdatesViewModel) { it.lce }

        vm.checkForUpdates()
        advanceUntilIdle()

        assertTrue(vm.state.value.notice == UpdatesNotice.CHECKED)
    }

    @Test
    fun `install dispatches the ApplicationUpdate command`() = runTest {
        seed()
        val vm = withViewModel(::LidarrUpdatesViewModel) { it.lce }

        vm.install()
        advanceUntilIdle()

        assertEquals("ApplicationUpdate", commands.commands.values.single().name)
        assertTrue(vm.state.value.notice == UpdatesNotice.INSTALL_STARTED)
    }

    @Test
    fun `install offline queues the command`() = runTest {
        seed()
        commands.error = { IOException("offline") }
        val vm = withViewModel(::LidarrUpdatesViewModel) { it.lce }

        vm.install()
        advanceUntilIdle()

        assertTrue(vm.state.value.notice == UpdatesNotice.INSTALL_QUEUED)
        assertEquals(1, queue.pending().size)
    }

    @Test
    fun `openLogFile loads the contents for the viewer`() = runTest {
        seed()
        api.logFileContents["lidarr.update.txt"] = "2026-10-20 10:00:00|Info|Update installed"
        val vm = withViewModel(::LidarrUpdatesViewModel) { it.lce }

        vm.openLogFile(vm.state.value.logFiles.first())
        advanceUntilIdle()

        assertEquals("lidarr.update.txt", vm.state.value.openLogFile!!.filename)
        assertTrue(vm.state.value.logContents!!.contains("Update installed"))
        assertNull(vm.state.value.logError)

        vm.closeLogFile()
        assertNull(vm.state.value.openLogFile)
    }

    @Test
    fun `log contents failure surfaces in the viewer only`() = runTest {
        seed()
        api.error = { op -> if (op == "getUpdateLogFile") IOException("offline") else null }
        val vm = withViewModel(::LidarrUpdatesViewModel) { it.lce }

        vm.openLogFile(vm.state.value.logFiles.first())
        advanceUntilIdle()

        assertNotNull(vm.state.value.logError)
        assertNull(vm.state.value.logContents)
        assertNull(vm.state.value.writeError)
    }

    @Test
    fun `no update rows renders the empty state`() = runTest {
        seed()
        api.updates.clear()
        val vm = withViewModel(::LidarrUpdatesViewModel) { it.lce }

        assertTrue(vm.lce.value is LceState.Empty)
    }
}
