package dev.appolon.homelab.arr.data

import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.net.ProxyConfig
import dev.appolon.homelab.arr.net.TrustSpec
import kotlinx.serialization.Serializable

/** One configured *arr instance. */
@Serializable
data class ArrServer(
    val id: ServerId,
    val type: AppType,
    val name: String,
    val baseUrl: String,
    /** Stored encrypted; never logged. */
    val apiKeyEncrypted: String,
    val basicAuthUser: String? = null,
    val basicAuthPasswordEncrypted: String? = null,
    val proxy: ProxyConfig? = null,
    val trust: TrustSpec? = null,
    val order: Int = 0,
    val createdAtMillis: Long = 0,
)

/** Registry contract for server CRUD. Implement with DataStore in :arr-data. */
interface ServerRegistry {
    suspend fun servers(): List<ArrServer>
    suspend fun serversOfType(type: AppType): List<ArrServer>
    suspend fun byId(id: ServerId): ArrServer?
    /** Returns the stored server with its generated id assigned. */
    suspend fun upsert(server: ArrServer): ArrServer
    suspend fun delete(id: ServerId)
    suspend fun reorder(idsInOrder: List<ServerId>)
    fun observe(): kotlinx.coroutines.flow.Flow<List<ArrServer>>
}

/**
 * Generic offline entity store backed by androidx.sqlite. One row per
 * (server, type, entity id) holding the serialized JSON plus fetchedAt.
 */
interface EntityStore {
    suspend fun <T> put(serverId: ServerId, type: EntityType, id: String, value: T, serializer: kotlinx.serialization.KSerializer<T>)
    suspend fun <T> putAll(serverId: ServerId, type: EntityType, values: List<Pair<String, T>>, serializer: kotlinx.serialization.KSerializer<T>, fetchedAtMillis: Long = System.currentTimeMillis())
    suspend fun <T> getAll(serverId: ServerId, type: EntityType, serializer: kotlinx.serialization.KSerializer<T>): List<T>
    suspend fun <T> get(serverId: ServerId, type: EntityType, id: String, serializer: kotlinx.serialization.KSerializer<T>): T?
    suspend fun delete(serverId: ServerId, type: EntityType, id: String)
    suspend fun deleteAll(serverId: ServerId, type: EntityType? = null)
    suspend fun lastFetchedAt(serverId: ServerId, type: EntityType): Long?
    fun <T> observe(serverId: ServerId, type: EntityType, serializer: kotlinx.serialization.KSerializer<T>): kotlinx.coroutines.flow.Flow<List<T>>
}

/** A replayable write captured while offline or on failure. */
@Serializable
data class QueuedAction(
    val actionId: ActionId,
    val serverId: ServerId,
    val appType: AppType,
    /** Retrofit-shaped descriptor: method, relative path, body JSON. */
    val method: String,
    val path: String,
    val bodyJson: String? = null,
    val description: String,
    val enqueuedAtMillis: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
)

/** Contract for the offline action queue. */
interface ActionQueue {
    suspend fun enqueue(action: QueuedAction)
    suspend fun pending(): List<QueuedAction>
    fun observe(): kotlinx.coroutines.flow.Flow<List<QueuedAction>>
    /** Replays oldest first, returns the actions that now succeeded or terminally failed. */
    suspend fun replay(executor: suspend (QueuedAction) -> Boolean): Pair<Int, Int>
    suspend fun remove(actionId: ActionId)
}
