package dev.appolon.homelab.arr.feature.radarr.settings.system.vm

import dev.appolon.homelab.arr.api.radarr.FakeRadarrQueue
import dev.appolon.homelab.arr.api.radarr.FakeRadarrSystem
import dev.appolon.homelab.arr.api.radarr.model.BackupResource
import dev.appolon.homelab.arr.api.radarr.model.BackupType
import dev.appolon.homelab.arr.api.radarr.model.HostConfigResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.settings.system.FakeSystemActionQueue
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.feature.radarr.settings.system.data.RadarrSettingsSystemRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RadarrBackupViewModelTest {

    private lateinit var api: FakeRadarrSystem
    private lateinit var commands: FakeRadarrQueue
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeSystemActionQueue
    private var now: Long = 1_000_000L

    @Before
    fun setUp() {
        api = FakeRadarrSystem()
        commands = FakeRadarrQueue()
        store = FakeEntityStore()
        queue = FakeSystemActionQueue()
        store.clock = { now }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.repository() = RadarrSettingsSystemRepository(
        serverId = ServerId("s1"),
        store = store,
        hostConfigApi = api,
        backupApi = api,
        updateApi = api,
        updateLogFileApi = api,
        systemApi = api,
        taskApi = api,
        diskSpaceApi = api,
        healthApi = api,
        tagApi = api,
        tagDetailsApi = api,
        autoTaggingApi = api,
        commandApi = commands,
        actionQueue = queue,
        now = { now },
        ioContext = StandardTestDispatcher(testScheduler),
    )

    private fun TestScope.withViewModel(): RadarrBackupViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = RadarrBackupViewModel(repository())
        backgroundScope.launch { vm.lce.collect { } }
        advanceUntilIdle()
        return vm
    }

    private fun seed() {
        api.hostConfigState = HostConfigResource(
            id = 1,
            backupFolder = "/config/Backups",
            backupInterval = 7,
            backupRetention = 28,
        )
        api.backups += BackupResource(
            id = 4,
            name = "radarr_backup.zip",
            type = BackupType.SCHEDULED,
            size = 2048,
            time = "2026-10-01T00:00:00Z",
        )
    }

    @Test
    fun `loads backups and the schedule draft`() = runTest {
        seed()
        val vm = withViewModel()
        val content = (vm.lce.value as LceState.Content).data

        assertEquals(1, content.backups.size)
        assertEquals("/config/Backups", content.schedule.folder)
        assertEquals("7", content.schedule.intervalDays)
        assertEquals("28", content.schedule.retentionDays)
    }

    @Test
    fun `createBackup dispatches the command and notices`() = runTest {
        seed()
        val vm = withViewModel()
        vm.createBackup()
        advanceUntilIdle()

        assertEquals("Backup", commands.commandItems.single().name)
        assertTrue(vm.state.value.notice == BackupNotice.CREATED)
    }

    @Test
    fun `restore requires confirmation and reports restart`() = runTest {
        seed()
        api.restoreResult = dev.appolon.homelab.arr.api.radarr.model.BackupRestoreResource(restartRequired = true)
        val vm = withViewModel()

        vm.requestRestore(vm.state.value.backups.first())
        assertNotNull(vm.state.value.confirmRestoreFor)
        vm.restoreConfirmed()
        advanceUntilIdle()

        assertTrue(api.restoreRequested)
        assertTrue(vm.state.value.notice == BackupNotice.RESTORED_RESTART)
        assertNull(vm.state.value.confirmRestoreFor)
    }

    @Test
    fun `delete confirms first and updates the list`() = runTest {
        seed()
        val vm = withViewModel()

        vm.requestDelete(vm.state.value.backups.first())
        vm.deleteConfirmed()
        advanceUntilIdle()

        assertTrue(api.backups.isEmpty())
        assertTrue(vm.state.value.notice == BackupNotice.DELETED)
    }

    @Test
    fun `schedule save PUTs the host config`() = runTest {
        seed()
        val vm = withViewModel()

        vm.editSchedule { it.copy(intervalDays = "3", retentionDays = "14") }
        vm.saveSchedule()
        advanceUntilIdle()

        assertEquals(3, api.hostConfigState.backupInterval)
        assertEquals(14, api.hostConfigState.backupRetention)
        assertTrue(vm.state.value.notice == BackupNotice.SCHEDULE_SAVED)
    }

    @Test
    fun `schedule with a non-numeric field never saves`() = runTest {
        seed()
        val vm = withViewModel()

        vm.editSchedule { it.copy(intervalDays = "weekly") }
        vm.saveSchedule()
        advanceUntilIdle()

        assertEquals(7, api.hostConfigState.backupInterval)
        assertNull(vm.state.value.notice)
    }

    @Test
    fun `offline delete queues and keeps the row marked pending`() = runTest {
        seed()
        val vm = withViewModel()

        api.errorFor = { IOException("offline") }
        vm.requestDelete(vm.state.value.backups.first())
        vm.deleteConfirmed()
        advanceUntilIdle()

        assertTrue(vm.state.value.notice == BackupNotice.DELETED_QUEUED)
        assertEquals("api/v3/system/backup/4", queue.pending().single().path)
    }
}
