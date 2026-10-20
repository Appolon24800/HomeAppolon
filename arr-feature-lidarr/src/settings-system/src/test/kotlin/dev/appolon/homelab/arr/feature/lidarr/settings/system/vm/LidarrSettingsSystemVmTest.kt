package dev.appolon.homelab.arr.feature.lidarr.settings.system.vm

import androidx.lifecycle.ViewModel
import dev.appolon.homelab.arr.api.lidarr.FakeLidarrQueue
import dev.appolon.homelab.arr.api.lidarr.FakeLidarrSystem
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.settings.system.FakeSystemActionQueue
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.feature.lidarr.settings.system.data.LidarrSettingsSystemRepository
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
 * [FakeLidarrSystem] behind the repository, an in-memory entity store and
 * action queue, and a controllable clock driving the TTL logic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class LidarrSettingsSystemVmTest {
    protected lateinit var api: FakeLidarrSystem
    protected lateinit var store: FakeEntityStore
    protected lateinit var queue: FakeSystemActionQueue
    protected lateinit var commands: FakeLidarrQueue
    protected var now: Long = 1_000_000L

    @Before
    fun setUpHarness() {
        api = FakeLidarrSystem()
        store = FakeEntityStore()
        queue = FakeSystemActionQueue()
        commands = FakeLidarrQueue()
        store.clock = { now }
    }

    @After
    fun tearDownHarness() {
        Dispatchers.resetMain()
    }

    protected fun TestScope.repository() = LidarrSettingsSystemRepository(
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

    /** Builds the ViewModel with Main on the test scheduler and collects its LCE. */
    protected fun <T : ViewModel> TestScope.withViewModel(
        create: (LidarrSettingsSystemRepository) -> T,
        lce: (T) -> kotlinx.coroutines.flow.StateFlow<*>,
    ): T {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = create(repository())
        backgroundScope.launch { lce(vm).collect { } }
        advanceUntilIdle()
        return vm
    }
}
