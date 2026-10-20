package dev.appolon.homelab.arr.feature.prowlarr.search

import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.KSerializer

/** Same serialized row and observation semantics as the sibling Radarr search fake. */
class FakeSearchEntityStore : EntityStore {
    private data class Key(val server: ServerId, val type: EntityType, val id: String)
    private data class Row(val json: String, val fetchedAt: Long)
    private val rows = mutableMapOf<Key, Row>()
    private val tick = MutableSharedFlow<Unit>(
        extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    var clock: () -> Long = System::currentTimeMillis

    override suspend fun <T> put(
        serverId: ServerId, type: EntityType, id: String, value: T, serializer: KSerializer<T>,
    ) {
        rows[Key(serverId, type, id)] = Row(ArrJson.encodeToString(serializer, value), clock())
        tick.tryEmit(Unit)
    }

    override suspend fun <T> putAll(
        serverId: ServerId, type: EntityType, values: List<Pair<String, T>>,
        serializer: KSerializer<T>, fetchedAtMillis: Long,
    ) {
        values.forEach { (id, value) ->
            rows[Key(serverId, type, id)] = Row(
                ArrJson.encodeToString(serializer, value), fetchedAtMillis,
            )
        }
        tick.tryEmit(Unit)
    }

    override suspend fun <T> getAll(
        serverId: ServerId, type: EntityType, serializer: KSerializer<T>,
    ): List<T> = rows.filterKeys { it.server == serverId && it.type == type }.values.map {
        ArrJson.decodeFromString(serializer, it.json)
    }

    override suspend fun <T> get(
        serverId: ServerId, type: EntityType, id: String, serializer: KSerializer<T>,
    ): T? = rows[Key(serverId, type, id)]?.let { ArrJson.decodeFromString(serializer, it.json) }

    override suspend fun delete(serverId: ServerId, type: EntityType, id: String) {
        rows.remove(Key(serverId, type, id))
        tick.tryEmit(Unit)
    }

    override suspend fun deleteAll(serverId: ServerId, type: EntityType?) {
        rows.keys.removeAll { it.server == serverId && (type == null || it.type == type) }
        tick.tryEmit(Unit)
    }

    override suspend fun lastFetchedAt(serverId: ServerId, type: EntityType): Long? =
        rows.filterKeys { it.server == serverId && it.type == type }.values.maxOfOrNull {
            it.fetchedAt
        }

    override fun <T> observe(
        serverId: ServerId, type: EntityType, serializer: KSerializer<T>,
    ): Flow<List<T>> = tick.onStart { emit(Unit) }
        .map { getAll(serverId, type, serializer) }.distinctUntilChanged()
}
