package dev.appolon.homelab.arr.data.testing

import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.EntityStore
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * TESTING SUPPORT, NOT APP CODE. This class lives in main source only so
 * every module's test tree can see it without a new dependency or Gradle
 * fixture plugin, the same mechanism :arr-core uses for its ArrTestServers
 * smoke-test helper. Nothing outside test trees may reference it.
 *
 * In-memory [EntityStore] mirroring the SQLite semantics the repositories
 * rely on: one row per (server, type, id), a fetchedAt stamp per row,
 * `lastFetchedAt` as the max over the scope, and observe() re-reading after
 * every write tick, deduplicated like [dev.appolon.homelab.arr.data.store.SqliteEntityStore].
 */
class FakeEntityStore(
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
