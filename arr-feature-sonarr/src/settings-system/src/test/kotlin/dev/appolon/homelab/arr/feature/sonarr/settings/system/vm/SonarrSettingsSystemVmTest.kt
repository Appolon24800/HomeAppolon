package dev.appolon.homelab.arr.feature.sonarr.settings.system.vm

import androidx.lifecycle.ViewModel
import dev.appolon.homelab.arr.api.sonarr.FakeSonarrQueue
import dev.appolon.homelab.arr.api.sonarr.FakeSonarrSystem
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.settings.system.FakeSystemActionQueue
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.feature.sonarr.settings.system.data.SonarrSettingsSystemRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before

/**
 * Shared harness for this group's ViewModel tests: the committed
 * [FakeSonarrSystem] behind the repository, an in-memory entity store and
 * action queue, and a controllable clock driving the TTL logic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class SonarrSettingsSystemVmTest {
    protected lateinit var api: FakeSonarrSystem
    protected lateinit var store: FakeEntityStore
    protected lateinit var queue: FakeSystemActionQueue
    protected lateinit var commands: FakeSonarrQueue
    protected var now: Long = 1_000_000L

    @Before
    fun setUpHarness() {
        api = FakeSonarrSystem()
        store = FakeEntityStore()
        queue = FakeSystemActionQueue()
        commands = FakeSonarrQueue()
        store.clock = { now }
    }

    @After
    fun tearDownHarness() {
        Dispatchers.resetMain()
    }

    protected fun TestScope.repository() = SonarrSettingsSystemRepository(
        serverId = ServerId("s1"),
        store = store,
        hostConfigApi = api.hostConfigApi,
        backupApi = api.backupApi,
        updateApi = api.updateApi,
        updateLogFileApi = api.updateLogFileApi,
        systemApi = api.systemApi,
        taskApi = api.taskApi,
        diskSpaceApi = api.diskSpaceApi,
        healthApi = api.healthApi,
        tagApi = api.tagApi,
        tagDetailsApi = api.tagDetailsApi,
        autoTaggingApi = api.autoTaggingApi,
        commandApi = commands.command,
        actionQueue = queue,
        now = { now },
        ioContext = StandardTestDispatcher(testScheduler),
    )

    /** Builds the ViewModel with Main on the test scheduler and collects its LCE. */
    protected fun <T : ViewModel> TestScope.withViewModel(
        create: (SonarrSettingsSystemRepository) -> T,
        lce: (T) -> kotlinx.coroutines.flow.StateFlow<*>,
    ): T {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = create(repository())
        backgroundScope.launch { lce(vm).collect { } }
        advanceUntilIdle()
        return vm
    }
}
