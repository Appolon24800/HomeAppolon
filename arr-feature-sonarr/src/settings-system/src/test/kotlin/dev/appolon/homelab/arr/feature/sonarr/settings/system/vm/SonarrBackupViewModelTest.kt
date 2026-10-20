package dev.appolon.homelab.arr.feature.sonarr.settings.system.vm

import dev.appolon.homelab.arr.api.sonarr.model.BackupResource
import dev.appolon.homelab.arr.api.sonarr.model.BackupType
import dev.appolon.homelab.arr.api.sonarr.model.HostConfigResource
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SonarrBackupViewModelTest : SonarrSettingsSystemVmTest() {

    private fun seed() {
        api.hostConfig = HostConfigResource(
            id = 1,
            backupFolder = "/config/backups",
            backupInterval = 7,
            backupRetention = 28,
        )
        api.backups += BackupResource(
            id = 4,
            name = "sonarr_backup.zip",
            type = BackupType.SCHEDULED,
            size = 2048,
            time = "2026-01-01T00:00:00Z",
        )
    }

    @Test
    fun `loads backups newest first with the schedule draft`() = runTest {
        seed()
        api.backups += BackupResource(
            id = 5,
            name = "newer.zip",
            type = BackupType.MANUAL,
            time = "2026-02-01T00:00:00Z",
        )

        val vm = withViewModel(::SonarrBackupViewModel) { it.lce }
        val content = (vm.lce.value as LceState.Content).data

        assertEquals(listOf(5, 4), content.backups.map { it.id })
        assertEquals("7", content.schedule.intervalDays)
        assertEquals("/config/backups", content.schedule.folder)
    }

    @Test
    fun `create dispatches the Backup command and reports started`() = runTest {
        seed()
        val vm = withViewModel(::SonarrBackupViewModel) { it.lce }
        vm.createBackup()
        advanceUntilIdle()

        assertEquals("Backup", commands.commandStore.values.single().name)
        assertTrue(vm.state.value.notice == BackupNotice.CREATED)
    }

    @Test
    fun `restore asks for confirmation then restores`() = runTest {
        seed()
        val vm = withViewModel(::SonarrBackupViewModel) { it.lce }

        vm.requestRestore(vm.state.value.backups.first())
        assertTrue(vm.state.value.confirmRestoreFor != null)
        vm.restoreConfirmed()
        advanceUntilIdle()

        assertEquals(listOf(4), api.restoredBackupIds)
        assertTrue(vm.state.value.notice == BackupNotice.RESTORED)
    }

    @Test
    fun `restore errors surface as write errors`() = runTest {
        seed()
        api.error = { op -> if (op == "Backup.restore") IOException("offline") else null }
        val vm = withViewModel(::SonarrBackupViewModel) { it.lce }

        vm.requestRestore(vm.state.value.backups.first())
        vm.restoreConfirmed()
        advanceUntilIdle()

        assertTrue(vm.state.value.writeError != null)
        assertTrue(vm.state.value.notice == null)
    }

    @Test
    fun `delete drops the row online`() = runTest {
        seed()
        val vm = withViewModel(::SonarrBackupViewModel) { it.lce }

        vm.requestDelete(vm.state.value.backups.first())
        vm.deleteConfirmed()
        advanceUntilIdle()

        assertTrue(api.backups.isEmpty())
        assertTrue(vm.state.value.notice == BackupNotice.DELETED)
    }

    @Test
    fun `delete offline queues and keeps the pending count`() = runTest {
        seed()
        api.error = { op -> if (op == "Backup.delete") IOException("offline") else null }
        val vm = withViewModel(::SonarrBackupViewModel) { it.lce }

        vm.requestDelete(vm.state.value.backups.first())
        vm.deleteConfirmed()
        advanceUntilIdle()

        assertTrue(vm.state.value.notice == BackupNotice.DELETED_QUEUED)
        assertEquals(1, vm.state.value.pendingActions)
    }

    @Test
    fun `schedule save writes the backup rows through the host config`() = runTest {
        seed()
        val vm = withViewModel(::SonarrBackupViewModel) { it.lce }

        vm.editSchedule { it.copy(intervalDays = "3", retentionDays = "14") }
        vm.saveSchedule()
        advanceUntilIdle()

        assertEquals(3, api.hostConfig.backupInterval)
        assertEquals(14, api.hostConfig.backupRetention)
        assertTrue(vm.state.value.notice == BackupNotice.SCHEDULE_SAVED)
    }

    @Test
    fun `empty list with no schedule edits renders the empty state`() = runTest {
        seed()
        api.backups.clear()
        val vm = withViewModel(::SonarrBackupViewModel) { it.lce }

        assertTrue(vm.lce.value is LceState.Empty)
    }
}
