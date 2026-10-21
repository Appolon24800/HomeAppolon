package dev.appolon.homelab.arr.feature.lidarr.settings.system

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrQueue
import dev.appolon.homelab.arr.api.lidarr.FakeLidarrSystem
import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.QueuedAction
import dev.appolon.homelab.arr.feature.lidarr.settings.system.data.LidarrSettingsSystemRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import dev.appolon.homelab.arr.data.testing.FakeEntityStore

/** In-memory [ActionQueue] with the same oldest-first semantics as the real one. */
class FakeSystemActionQueue : ActionQueue {

    val pendingFlow = MutableStateFlow<List<QueuedAction>>(emptyList())

    override suspend fun enqueue(action: QueuedAction) {
        pendingFlow.value = (pendingFlow.value + action).sortedBy { it.enqueuedAtMillis }
    }

    override suspend fun pending(): List<QueuedAction> = pendingFlow.value

    override fun observe(): Flow<List<QueuedAction>> = pendingFlow

    override suspend fun replay(executor: suspend (QueuedAction) -> Boolean): Pair<Int, Int> {
        var succeeded = 0
        var failed = 0
        pendingFlow.value.toList().forEach { action ->
            if (executor(action)) {
                pendingFlow.value = pendingFlow.value - action
                succeeded++
            } else {
                failed++
            }
        }
        return succeeded to failed
    }

    override suspend fun remove(actionId: ActionId) {
        pendingFlow.value = pendingFlow.value.filterNot { it.actionId == actionId }
    }

    fun action(
        id: String,
        serverId: ServerId = ServerId("s1"),
        path: String = "api/v1/tag",
    ): QueuedAction = QueuedAction(
        actionId = ActionId(id),
        serverId = serverId,
        appType = AppType.LIDARR,
        method = "PUT",
        path = path,
        description = "test action",
        enqueuedAtMillis = pendingFlow.value.size.toLong(),
    )
}

/**
 * One [LidarrSettingsSystemRepository] over the committed fakes:
 * [FakeLidarrSystem] implements every interface this group uses, and
 * [FakeLidarrQueue] records the named commands (Backup,
 * ApplicationUpdate).
 */
fun systemRepository(
    serverId: ServerId = ServerId("s1"),
    store: FakeEntityStore = FakeEntityStore(),
    system: FakeLidarrSystem = FakeLidarrSystem(),
    queue: FakeLidarrQueue = FakeLidarrQueue(),
    actionQueue: FakeSystemActionQueue = FakeSystemActionQueue(),
    now: () -> Long = System::currentTimeMillis,
): LidarrSettingsSystemRepository = LidarrSettingsSystemRepository(
    serverId = serverId,
    store = store,
    hostConfigApi = system,
    backupApi = system,
    updateApi = system,
    updateLogFileApi = system,
    systemApi = system,
    taskApi = system,
    diskSpaceApi = system,
    healthApi = system,
    tagApi = system,
    tagDetailsApi = system,
    autoTaggingApi = system,
    commandApi = queue,
    actionQueue = actionQueue,
    now = now,
    ioContext = Dispatchers.Unconfined,
)
