package dev.appolon.homelab.arr.feature.sonarr.calendar

import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer

/**
 * In-memory [EntityStore] mirroring the SQLite contract closely enough for
 * calendar repository and ViewModel tests: rows keyed by (server, type, id),
 * observe re-emits per type on every write, and putAll stamps the shared
 * fetchedAt.
 */
class FakeCalendarEntityStore : EntityStore {

    private data class Row(val serverId: ServerId, val type: EntityType, val id: String, val json: String)

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
            Row(serverId, type, id, ArrJson.encodeToString(serializer, value))
        }
        rows.value = rows.value.filterNot { row ->
            row.serverId == serverId && row.type == type && row.id in values.map { it.first }
        } + encoded
        fetchedAt[serverId to type] = fetchedAtMillis
    }

    override suspend fun <T> getAll(serverId: ServerId, type: EntityType, serializer: KSerializer<T>): List<T> =
        rows.value.filter { it.serverId == serverId && it.type == type }
            .map { decode(it, serializer) }

    override suspend fun <T> get(serverId: ServerId, type: EntityType, id: String, serializer: KSerializer<T>): T? =
        rows.value.firstOrNull { it.serverId == serverId && it.type == type && it.id == id }
            ?.let { decode(it, serializer) }

    override suspend fun delete(serverId: ServerId, type: EntityType, id: String) {
        rows.value = rows.value.filterNot { it.serverId == serverId && it.type == type && it.id == id }
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
        ArrJson.decodeFromString(serializer, row.json)
}
