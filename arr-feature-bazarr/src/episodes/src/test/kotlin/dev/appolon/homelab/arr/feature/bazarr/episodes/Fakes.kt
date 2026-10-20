package dev.appolon.homelab.arr.feature.bazarr.episodes

import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.QueuedAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer

/**
 * In-memory [EntityStore] mirroring the SQLite contract closely enough for
 * repository and ViewModel tests: rows keyed by (server, type, id), observe
 * re-emits per type on every write, and putAll stamps the shared fetchedAt.
 */
class FakeEntityStore : EntityStore {

    private data class Row(
        val serverId: ServerId,
        val type: EntityType,
        val id: String,
        val json: String,
    )

    private val rows = MutableStateFlow<List<Row>>(emptyList())
    private val fetchedAt = HashMap<Pair<ServerId, EntityType>, Long>()

    override suspend fun <T> put(
        serverId: ServerId,
        type: EntityType,
        id: String,
        value: T,
        serializer: KSerializer<T>,
    ) {
        putAll(serverId, type, listOf(id to value), serializer)
    }

    override suspend fun <T> putAll(
        serverId: ServerId,
        type: EntityType,
        values: List<Pair<String, T>>,
        serializer: KSerializer<T>,
        fetchedAtMillis: Long,
    ) {
        val encoded = values.map { (id, value) ->
            Row(
                serverId, type, id,
                dev.appolon.homelab.arr.net.ArrJson.encodeToString(serializer, value),
            )
        }
        rows.value = rows.value.filterNot { row ->
            row.serverId == serverId && row.type == type && row.id in values.map { it.first }
        } + encoded
        fetchedAt[serverId to type] = fetchedAtMillis
    }

    override suspend fun <T> getAll(
        serverId: ServerId,
        type: EntityType,
        serializer: KSerializer<T>,
    ): List<T> =
        rows.value.filter { it.serverId == serverId && it.type == type }
            .map { decode(it, serializer) }

    override suspend fun <T> get(
        serverId: ServerId,
        type: EntityType,
        id: String,
        serializer: KSerializer<T>,
    ): T? =
        rows.value.firstOrNull { it.serverId == serverId && it.type == type && it.id == id }
            ?.let { decode(it, serializer) }

    override suspend fun delete(serverId: ServerId, type: EntityType, id: String) {
        rows.value = rows.value.filterNot {
            it.serverId == serverId && it.type == type && it.id == id
        }
    }

    override suspend fun deleteAll(serverId: ServerId, type: EntityType?) {
        rows.value = rows.value.filterNot {
            it.serverId == serverId && (type == null || it.type == type)
        }
    }

    override suspend fun lastFetchedAt(serverId: ServerId, type: EntityType): Long? =
        fetchedAt[serverId to type]

    override fun <T> observe(
        serverId: ServerId,
        type: EntityType,
        serializer: KSerializer<T>,
    ): Flow<List<T>> = rows.map { snapshot ->
        snapshot.filter { it.serverId == serverId && it.type == type }
            .map { decode(it, serializer) }
    }

    private fun <T> decode(row: Row, serializer: KSerializer<T>): T =
        dev.appolon.homelab.arr.net.ArrJson.decodeFromString(serializer, row.json)

    /** Exposed for assertions about cross-type isolation. */
    fun rowKeys(): Set<Triple<ServerId, EntityType, String>> = rows.value
        .map { Triple(it.serverId, it.type, it.id) }
        .toSet()
}

/** In-memory [ActionQueue] with the same oldest-first semantics as the real one. */
class FakeActionQueue : ActionQueue {

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
        path: String = "episodes/subtitles",
    ): QueuedAction = QueuedAction(
        actionId = ActionId(id),
        serverId = serverId,
        appType = AppType.BAZARR,
        method = "PATCH",
        path = path,
        description = "test action",
        enqueuedAtMillis = pendingFlow.value.size.toLong(),
    )
}
