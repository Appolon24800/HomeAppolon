package dev.appolon.homelab.arr.feature.radarr.detail

import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.EntityStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer

/**
 * In-memory [EntityStore] for unit tests. Values keep their object identity
 * (serializers are ignored) and an injectable clock drives deterministic
 * fetchedAt stamps for TTL assertions.
 */
class FakeDetailEntityStore(
    private val clock: () -> Long = { 0L },
) : EntityStore {
    private val rows = LinkedHashMap<Triple<String, String, String>, Any>()
    private val fetched = HashMap<Pair<String, String>, Long>()
    private val version = MutableStateFlow(0)

    override suspend fun <T> put(
        serverId: ServerId,
        type: EntityType,
        id: String,
        value: T,
        serializer: KSerializer<T>,
    ) {
        rows[key(serverId, type, id)] = value!!
        stamp(serverId, type, clock())
    }

    override suspend fun <T> putAll(
        serverId: ServerId,
        type: EntityType,
        values: List<Pair<String, T>>,
        serializer: KSerializer<T>,
        fetchedAtMillis: Long,
    ) {
        values.forEach { (id, value) -> rows[key(serverId, type, id)] = value!! }
        stamp(serverId, type, fetchedAtMillis)
    }

    override suspend fun <T> getAll(
        serverId: ServerId,
        type: EntityType,
        serializer: KSerializer<T>,
    ): List<T> = rows.values.filterKeysFor(serverId, type)

    override suspend fun <T> get(
        serverId: ServerId,
        type: EntityType,
        id: String,
        serializer: KSerializer<T>,
    ): T? = rows[key(serverId, type, id)] as T?

    override suspend fun delete(serverId: ServerId, type: EntityType, id: String) {
        rows.remove(key(serverId, type, id))
    }

    override suspend fun deleteAll(serverId: ServerId, type: EntityType?) {
        rows.keys.removeAll { (server, t, _) ->
            server == serverId.value && (type == null || t == type.value)
        }
    }

    override suspend fun lastFetchedAt(serverId: ServerId, type: EntityType): Long? =
        fetched[serverId.value to type.value]

    override fun <T> observe(
        serverId: ServerId,
        type: EntityType,
        serializer: KSerializer<T>,
    ): Flow<List<T>> = version.map { getAll(serverId, type, serializer) }

    /** Row count of one type, for assertions. */
    fun count(serverId: ServerId, type: EntityType): Int =
        rows.keys.count { (server, t, _) -> server == serverId.value && t == type.value }

    private fun stamp(serverId: ServerId, type: EntityType, at: Long) {
        val k = serverId.value to type.value
        fetched[k] = maxOf(fetched[k] ?: Long.MIN_VALUE, at)
        version.value = version.value + 1
    }

    private fun key(serverId: ServerId, type: EntityType, id: String) =
        Triple(serverId.value, type.value, id)

    @Suppress("UNCHECKED_CAST")
    private fun <T> Collection<Any>.filterKeysFor(serverId: ServerId, type: EntityType): List<T> =
        rows.entries
            .filter { (k, _) -> k.first == serverId.value && k.second == type.value }
            .map { it.value as T }
}
