package dev.appolon.homelab.arr.feature.sonarr.settings.system.vm

import dev.appolon.homelab.arr.api.sonarr.model.DatabaseType
import dev.appolon.homelab.arr.api.sonarr.model.DiskSpaceResource
import dev.appolon.homelab.arr.api.sonarr.model.HealthCheckResult
import dev.appolon.homelab.arr.api.sonarr.model.HealthResource
import dev.appolon.homelab.arr.api.sonarr.model.SystemResource
import dev.appolon.homelab.arr.api.sonarr.model.TaskResource
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SonarrSystemStatusViewModelTest : SonarrSettingsSystemVmTest() {

    private fun seed() {
        api.systemStatus = SystemResource(
            appName = "Sonarr",
            version = "4.0.12.2278",
            migrationVersion = 210,
            databaseType = DatabaseType.SQLITE,
            databaseVersion = "3.44.2",
            runtimeName = ".NET",
            runtimeVersion = "8.0.11",
        )
        api.taskStore[1] = TaskResource(
            id = 1,
            name = "Backup",
            interval = 1440,
            lastDuration = "00:00:02.5000000",
            lastExecution = "2026-10-19T00:00:00Z",
        )
        api.diskSpace += DiskSpaceResource(id = 1, path = "/tv", freeSpace = 100, totalSpace = 400)
        api.health += HealthResource(
            id = 1,
            type = HealthCheckResult.WARNING,
            message = "Recycling bin is not configured",
            wikiUrl = "https://wiki.servarr.com/sonarr/system#recycling-bin",
        )
    }

    @Test
    fun `loads status tasks disk and health with errors ranked first`() = runTest {
        seed()
        api.health += HealthResource(id = 2, type = HealthCheckResult.ERROR, message = "broken")
        val vm = withViewModel(::SonarrSystemStatusViewModel) { it.lce }
        val content = (vm.lce.value as LceState.Content).data

        assertEquals("4.0.12.2278", content.status.version)
        assertEquals(210, content.status.migrationVersion)
        assertEquals(1, content.tasks.size)
        assertEquals(2L, content.tasks.first().lastDurationSeconds)
        assertEquals(1, content.diskSpace.size)
        assertEquals(HealthCheckResult.ERROR, content.health.first().type)
    }

    @Test
    fun `a fresh ViewModel starts in loading before the first emission`() = runTest {
        seed()
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = SonarrSystemStatusViewModel(repository())

        assertEquals(LceState.Loading, vm.lce.value)

        backgroundScope.launch { vm.lce.collect { } }
        advanceUntilIdle()

        assertTrue(vm.lce.value is LceState.Content)
    }

    @Test
    fun `network failure with no cache surfaces the error state`() = runTest {
        api.error = { IOException("offline") }
        val vm = withViewModel(::SonarrSystemStatusViewModel) { it.lce }

        assertTrue(vm.lce.value is LceState.Error)
    }

    @Test
    fun `cached rows keep rendering when a later refresh fails`() = runTest {
        seed()
        val vm = withViewModel(::SonarrSystemStatusViewModel) { it.lce }
        assertTrue(vm.lce.value is LceState.Content)

        now += dev.appolon.homelab.arr.feature.sonarr.settings.system.data
            .SonarrSettingsSystemRepository.SYSTEM_TTL_MILLIS + 1
        api.error = { IOException("offline") }
        vm.refresh()
        advanceUntilIdle()

        assertTrue(vm.lce.value is LceState.Content)
        assertNull(vm.state.value.loadError)
    }
}
