package dev.appolon.homelab.arr.feature.sonarr.library

import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.EntityStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer

/**
 * In-memory EntityStore for unit tests: rows keyed by (server, type, id),
 * with write-driven invalidation like the SQLite implementation.
 */
class FakeEntityStore : EntityStore {

    var writeFailure: Throwable? = null

    private data class Key(val serverId: ServerId, val type: EntityType, val id: String)

    private val rows = LinkedHashMap<Key, Pair<String, String>>() // key -> (json, fetchedAt)
    private val refreshTimes = mutableMapOf<Pair<ServerId, EntityType>, Long>()
    private val tick = MutableStateFlow(0)
    val writes: Int get() = tick.value

    override suspend fun <T> put(
        serverId: ServerId,
        type: EntityType,
        id: String,
        value: T,
        serializer: KSerializer<T>,
    ) {
        writeFailure?.let { throw it }
        rows[Key(serverId, type, id)] =
            dev.appolon.homelab.arr.net.ArrJson.encodeToString(serializer, value) to
                System.currentTimeMillis().toString()
        tick.value++
    }

    override suspend fun <T> putAll(
        serverId: ServerId,
        type: EntityType,
        values: List<Pair<String, T>>,
        serializer: KSerializer<T>,
        fetchedAtMillis: Long,
    ) {
        writeFailure?.let { throw it }
        values.forEach { (id, value) ->
            rows[Key(serverId, type, id)] =
                dev.appolon.homelab.arr.net.ArrJson.encodeToString(serializer, value) to
                    fetchedAtMillis.toString()
        }
        tick.value++
    }

    override suspend fun <T> replaceAll(
        serverId: ServerId,
        type: EntityType,
        values: List<Pair<String, T>>,
        serializer: KSerializer<T>,
        fetchedAtMillis: Long,
    ) {
        writeFailure?.let { throw it }
        val encoded = values.map { (id, value) ->
            Key(serverId, type, id) to (dev.appolon.homelab.arr.net.ArrJson.encodeToString(serializer, value) to fetchedAtMillis.toString())
        }
        rows.keys.removeAll { it.serverId == serverId && it.type == type }
        rows.putAll(encoded)
        refreshTimes[serverId to type] = fetchedAtMillis
        tick.value++
    }

    override suspend fun <T> getAll(
        serverId: ServerId,
        type: EntityType,
        serializer: KSerializer<T>,
    ): List<T> = rows.entries
        .filter { it.key.serverId == serverId && it.key.type == type }
        .map { dev.appolon.homelab.arr.net.ArrJson.decodeFromString(serializer, it.value.first) }

    override suspend fun <T> get(
        serverId: ServerId,
        type: EntityType,
        id: String,
        serializer: KSerializer<T>,
    ): T? = rows[Key(serverId, type, id)]
        ?.let { dev.appolon.homelab.arr.net.ArrJson.decodeFromString(serializer, it.first) }

    override suspend fun delete(serverId: ServerId, type: EntityType, id: String) {
        rows.remove(Key(serverId, type, id))
        tick.value++
    }

    override suspend fun deleteAll(serverId: ServerId, type: EntityType?) {
        rows.keys.removeAll { it.serverId == serverId && (type == null || it.type == type) }
        refreshTimes.keys.removeAll { it.first == serverId && (type == null || it.second == type) }
        tick.value++
    }

    override suspend fun lastFetchedAt(serverId: ServerId, type: EntityType): Long? =
        listOfNotNull(
            refreshTimes[serverId to type],
            rows.entries.filter { it.key.serverId == serverId && it.key.type == type }
                .maxOfOrNull { it.value.second.toLong() },
        ).maxOrNull()

    override fun <T> observe(
        serverId: ServerId,
        type: EntityType,
        serializer: KSerializer<T>,
    ): Flow<List<T>> = tick
        .map { getAll(serverId, type, serializer) }
        .distinctUntilChanged()
}
