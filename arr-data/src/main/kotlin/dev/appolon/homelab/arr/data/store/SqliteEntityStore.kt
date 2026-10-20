package dev.appolon.homelab.arr.data.store

import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataJson
import dev.appolon.homelab.arr.data.EntityStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * [EntityStore] over any [ArrDatabase]. The json column carries the
 * serialized entity, so cached entity schemas evolve without SQL changes.
 *
 * Reads and writes run on Dispatchers.IO; writes serialize behind a mutex and
 * publish an invalidation tick that [observe] collectors turn into a fresh
 * read, deduplicated with distinctUntilChanged.
 */
class SqliteEntityStore internal constructor(
    private val db: ArrDatabase,
    private val json: Json = ArrDataJson,
) : EntityStore {

    private val writeTick = MutableSharedFlow<Unit>(
        extraBufferCapacity = INVALIDATION_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val writeMutex = Mutex()

    override suspend fun <T> put(
        serverId: ServerId,
        type: EntityType,
        id: String,
        value: T,
        serializer: KSerializer<T>,
    ) {
        val encoded = json.encodeToString(serializer, value)
        write(serverId, type, id, encoded, System.currentTimeMillis())
    }

    override suspend fun <T> putAll(
        serverId: ServerId,
        type: EntityType,
        values: List<Pair<String, T>>,
        serializer: KSerializer<T>,
        fetchedAtMillis: Long,
    ) {
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                db.inTransaction {
                    values.forEach { (id, value) ->
                        db.execute(
                            EntitySql.UPSERT,
                            listOf(
                                serverId.value,
                                type.value,
                                id,
                                json.encodeToString(serializer, value),
                                fetchedAtMillis,
                            ),
                        )
                    }
                }
            }
        }
        invalidate()
    }

    override suspend fun <T> getAll(
        serverId: ServerId,
        type: EntityType,
        serializer: KSerializer<T>,
    ): List<T> = withContext(Dispatchers.IO) {
        db.query(EntitySql.SELECT_BY_SCOPE, listOf(serverId.value, type.value))
            .map { row -> json.decodeFromString(serializer, EntityRow(row).json) }
    }

    override suspend fun <T> get(
        serverId: ServerId,
        type: EntityType,
        id: String,
        serializer: KSerializer<T>,
    ): T? = withContext(Dispatchers.IO) {
        db.query(EntitySql.SELECT_ONE, listOf(serverId.value, type.value, id))
            .firstOrNull()
            ?.let { json.decodeFromString(serializer, it[0] as String) }
    }

    override suspend fun delete(serverId: ServerId, type: EntityType, id: String) {
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                db.execute(EntitySql.DELETE_ONE, listOf(serverId.value, type.value, id))
            }
        }
        invalidate()
    }

    override suspend fun deleteAll(serverId: ServerId, type: EntityType?) {
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                val args = listOfNotNull(serverId.value, type?.value)
                db.execute(EntitySql.deleteAllFor(type?.value), args)
            }
        }
        invalidate()
    }

    override suspend fun lastFetchedAt(serverId: ServerId, type: EntityType): Long? =
        withContext(Dispatchers.IO) {
            db.query(EntitySql.MAX_FETCHED_AT, listOf(serverId.value, type.value))
                .firstOrNull()
                ?.get(0) as? Long
        }

    override fun <T> observe(
        serverId: ServerId,
        type: EntityType,
        serializer: KSerializer<T>,
    ): Flow<List<T>> =
        writeTick.onStart { emit(Unit) }
            .map { getAll(serverId, type, serializer) }
            .distinctUntilChanged()

    /** Closes the underlying database; call once at graph teardown. */
    fun close() = db.close()

    private suspend fun write(
        serverId: ServerId,
        type: EntityType,
        id: String,
        encoded: String,
        fetchedAtMillis: Long,
    ) {
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                db.execute(
                    EntitySql.UPSERT,
                    listOf(serverId.value, type.value, id, encoded, fetchedAtMillis),
                )
            }
        }
        invalidate()
    }

    private fun invalidate() {
        writeTick.tryEmit(Unit)
    }

    private companion object {
        const val INVALIDATION_BUFFER = 256
    }
}
