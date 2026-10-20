package dev.appolon.homelab.arr.feature.sonarr.settings.connect

import dev.appolon.homelab.arr.api.sonarr.CommandApi
import dev.appolon.homelab.arr.api.sonarr.MetadataApi
import dev.appolon.homelab.arr.api.sonarr.TagApi
import dev.appolon.homelab.arr.api.sonarr.model.CommandResource
import dev.appolon.homelab.arr.api.sonarr.model.MetadataResource
import dev.appolon.homelab.arr.api.sonarr.model.TagResource
import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.QueuedAction
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

/**
 * In-memory [EntityStore] mirroring the SQLite semantics the connect
 * repository relies on: one row per (server, type, id), a fetchedAt stamp
 * per row, `lastFetchedAt` as the max over the type, and observe()
 * re-reading after every write tick.
 */
class FakeConnectEntityStore(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        encodeDefaults = true
    },
) : EntityStore {

    private data class Row(
        val serverId: String,
        val type: String,
        val id: String,
        val json: String,
        val fetchedAt: Long,
    )

    private val rows = mutableListOf<Row>()
    private val tick = MutableSharedFlow<Unit>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Stamp for single-row puts, so tests can drive the TTL logic. */
    var clock: () -> Long = System::currentTimeMillis

    override suspend fun <T> put(
        serverId: ServerId,
        type: EntityType,
        id: String,
        value: T,
        serializer: KSerializer<T>,
    ) {
        upsert(serverId, type, id, json.encodeToString(serializer, value), clock())
    }

    override suspend fun <T> putAll(
        serverId: ServerId,
        type: EntityType,
        values: List<Pair<String, T>>,
        serializer: KSerializer<T>,
        fetchedAtMillis: Long,
    ) {
        values.forEach { (id, value) ->
            upsert(serverId, type, id, json.encodeToString(serializer, value), fetchedAtMillis)
        }
        tick.tryEmit(Unit)
    }

    override suspend fun <T> getAll(
        serverId: ServerId,
        type: EntityType,
        serializer: KSerializer<T>,
    ): List<T> = snapshot(serverId, type).map { json.decodeFromString(serializer, it.json) }

    override suspend fun <T> get(
        serverId: ServerId,
        type: EntityType,
        id: String,
        serializer: KSerializer<T>,
    ): T? = snapshot(serverId, type)
        .firstOrNull { it.id == id }
        ?.let { json.decodeFromString(serializer, it.json) }

    override suspend fun delete(serverId: ServerId, type: EntityType, id: String) {
        rows.removeAll { it.serverId == serverId.value && it.type == type.value && it.id == id }
        tick.tryEmit(Unit)
    }

    override suspend fun deleteAll(serverId: ServerId, type: EntityType?) {
        rows.removeAll { row ->
            row.serverId == serverId.value && (type == null || row.type == type.value)
        }
        tick.tryEmit(Unit)
    }

    override suspend fun lastFetchedAt(serverId: ServerId, type: EntityType): Long? =
        snapshot(serverId, type).maxOfOrNull { it.fetchedAt }

    override fun <T> observe(
        serverId: ServerId,
        type: EntityType,
        serializer: KSerializer<T>,
    ): Flow<List<T>> = tick.onStart { emit(Unit) }
        .map { getAll(serverId, type, serializer) }
        .distinctUntilChanged()

    private fun snapshot(serverId: ServerId, type: EntityType): List<Row> =
        rows.filter { it.serverId == serverId.value && it.type == type.value }.toList()

    private fun upsert(
        serverId: ServerId,
        type: EntityType,
        id: String,
        encoded: String,
        fetchedAt: Long,
    ) {
        rows.removeAll { it.serverId == serverId.value && it.type == type.value && it.id == id }
        rows += Row(serverId.value, type.value, id, encoded, fetchedAt)
        tick.tryEmit(Unit)
    }
}

/** In-memory [ActionQueue] with the same oldest-first semantics as the real one. */
class FakeConnectActionQueue : ActionQueue {

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
        path: String = "api/v3/indexer",
    ): QueuedAction = QueuedAction(
        actionId = ActionId(id),
        serverId = serverId,
        appType = AppType.SONARR,
        method = "PUT",
        path = path,
        description = "test action",
        enqueuedAtMillis = pendingFlow.value.size.toLong(),
    )
}

/** Minimal mutable [TagApi] matching the Sonarr wire contract. */
class FakeConnectTagApi : TagApi {
    val tags = mutableListOf<TagResource>()

    override suspend fun list(): List<TagResource> = tags.toList()

    override suspend fun create(body: TagResource): TagResource {
        val created = body.copy(id = (tags.maxOfOrNull { it.id } ?: 0) + 1)
        tags += created
        return created
    }

    override suspend fun update(id: String, body: TagResource): TagResource {
        val idInt = id.toIntOrNull() ?: error("bad id")
        val index = tags.indexOfFirst { it.id == idInt }
        if (index < 0) error("missing tag")
        tags[index] = body.copy(id = idInt)
        return tags[index]
    }

    override suspend fun delete(id: Int): Unit {
        tags.removeAll { it.id == id }
    }

    override suspend fun get(id: Int): Response<TagResource> {
        val found = tags.firstOrNull { it.id == id }
            ?: return Response.error(404, "{}".toResponseBody())
        return Response.success(found)
    }
}

/** Records the command bodies posted through [create]. */
class FakeConnectCommandApi : CommandApi {
    val started = mutableListOf<CommandResource>()

    override suspend fun create(body: CommandResource): CommandResource {
        started += body
        return body
    }

    override suspend fun list(): List<CommandResource> = emptyList()

    override suspend fun delete(id: Int): Unit = Unit

    override suspend fun get(id: Int): Response<CommandResource> =
        Response.error(404, "{}".toResponseBody())
}

/** Minimal mutable [MetadataApi] matching the Sonarr wire contract. */
class FakeConnectMetadataApi : MetadataApi {
    val rows = mutableListOf<MetadataResource>()
    val schemas = mutableListOf<MetadataResource>()
    val tested = mutableListOf<MetadataResource>()
    val testedAllCount = mutableListOf<Int>()
    private var nextId = 1

    override suspend fun list(): List<MetadataResource> = rows.toList()

    override suspend fun create(
        forceSave: Boolean?,
        body: MetadataResource,
    ): MetadataResource {
        val created = body.copy(id = nextId++)
        rows += created
        return created
    }

    override suspend fun update(
        id: Int,
        forceSave: Boolean?,
        body: MetadataResource,
    ): MetadataResource {
        val index = rows.indexOfFirst { it.id == id }
        if (index < 0) error("missing metadata consumer")
        rows[index] = body.copy(id = id)
        return rows[index]
    }

    override suspend fun delete(id: Int): Unit {
        rows.removeAll { it.id == id }
    }

    override suspend fun get(id: Int): Response<MetadataResource> {
        val found = rows.firstOrNull { it.id == id }
            ?: return Response.error(404, "{}".toResponseBody())
        return Response.success(found)
    }

    override suspend fun schema(): List<MetadataResource> = schemas.toList()

    override suspend fun test(forceTest: Boolean?, body: MetadataResource): Unit {
        tested += body
    }

    override suspend fun testAll(): Unit {
        testedAllCount.add(rows.size)
    }

    override suspend fun action(name: String, body: MetadataResource): Unit = Unit
}
