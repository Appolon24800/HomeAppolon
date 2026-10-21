package dev.appolon.homelab.arr.feature.lidarr.queue.data

import dev.appolon.homelab.arr.api.lidarr.QueueActionApi
import dev.appolon.homelab.arr.api.lidarr.QueueApi
import dev.appolon.homelab.arr.api.lidarr.QueueDetailsApi
import dev.appolon.homelab.arr.api.lidarr.QueueStatusApi
import dev.appolon.homelab.arr.api.lidarr.model.QueueResource
import dev.appolon.homelab.arr.api.lidarr.model.QueueStatusResource
import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.QueuedAction
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.net.arrError
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import kotlin.coroutines.CoroutineContext

/** Options for removing one queue item, mirroring Lidarr's remove dialog. */
data class QueueRemoveOptions(
    val removeFromClient: Boolean = true,
    val blocklist: Boolean = false,
    /** Re-search a replacement after removal; with [blocklist] this maps to `skipRedownload=false`. */
    val searchReplacement: Boolean = false,
)

/** How a queue write landed: applied on the server, or queued for replay. */
sealed interface QueueWriteOutcome {
    data object Synced : QueueWriteOutcome
    data class Queued(val actionId: ActionId) : QueueWriteOutcome
}

/**
 * Offline queue writes parsed back into queue item targets, so the list can
 * badge items whose action has not replayed yet.
 */
data class QueuedQueueWrites(
    val removedItemIds: Set<Int> = emptySet(),
    val grabbedItemIds: Set<Int> = emptySet(),
    val retriedDownloadIds: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = removedItemIds.isEmpty() &&
        grabbedItemIds.isEmpty() &&
        retriedDownloadIds.isEmpty()

    /** Queue item ids carrying a queued write, matched against [items] by download id. */
    fun queuedItemIds(items: List<QueueResource>): Set<Int> {
        val retriedItems = items
            .filter { it.downloadId != null && it.downloadId in retriedDownloadIds }
            .map { it.id }
        return (grabbedItemIds + retriedItems).toSet()
    }
}

/**
 * Queue repository. Reads are cache-first through the [EntityStore]: observe
 * plus a fetchedAt TTL driving refresh; the list itself comes from the
 * QueueDetails endpoint (unpaged, includes unknown artist items) and the
 * header counts from QueueStatus. Writes go to the server first and fall back
 * to an optimistic cache change plus [ActionQueue] enqueue when the network
 * call fails with [ArrError.Offline] or [ArrError.Network]. Everything runs
 * on IO; no function throws.
 */
class LidarrQueueRepository(
    private val serverId: ServerId,
    private val store: EntityStore,
    private val queueDetailsApi: QueueDetailsApi,
    private val queueApi: QueueApi,
    private val queueActionApi: QueueActionApi,
    private val queueStatusApi: QueueStatusApi,
    private val commandRunner: QueueCommandRunner,
    private val actionQueue: ActionQueue,
    private val json: kotlinx.serialization.json.Json = ArrJson,
    private val now: () -> Long = System::currentTimeMillis,
    /** Injectable so unit tests run the whole pipeline on virtual time. */
    private val ioContext: CoroutineContext = Dispatchers.IO,
) {

    fun observeItems(): Flow<List<QueueResource>> =
        store.observe(serverId, QUEUE_ITEM_TYPE, QueueResource.serializer())

    fun observeStatus(): Flow<QueueStatusResource?> =
        store.observe(serverId, QUEUE_STATUS_TYPE, QueueStatusResource.serializer())
            .map { rows -> rows.firstOrNull() }

    suspend fun cachedItems(): List<QueueResource> =
        store.getAll(serverId, QUEUE_ITEM_TYPE, QueueResource.serializer())

    /** True when the queue cache is absent or older than [ttlMillis]. */
    suspend fun isStale(ttlMillis: Long = QUEUE_TTL_MILLIS): Boolean {
        val fetched = store.lastFetchedAt(serverId, QUEUE_ITEM_TYPE) ?: return true
        return now() - fetched > ttlMillis
    }

    /** Fetches details plus status and reconciles the cache row by row. */
    suspend fun refresh(): ArrResult<Unit> = withContext(ioContext) {
        val fetched = try {
            queueDetailsApi.getQueueDetails(includeArtist = true, includeAlbum = true)
        } catch (e: HttpException) {
            return@withContext ArrResult.err(e.toArrError())
        } catch (e: IOException) {
            return@withContext ArrResult.err(ArrError.Network(e))
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrError())
        }
        val incoming = fetched.associateBy { it.id }
        cachedItems().forEach { cached ->
            val id = cached.id.toString()
            val replacement = incoming[cached.id]
            if (replacement != null) {
                store.put(serverId, QUEUE_ITEM_TYPE, id, replacement, QueueResource.serializer())
            } else {
                store.delete(serverId, QUEUE_ITEM_TYPE, id)
            }
        }
        store.putAll(
            serverId,
            QUEUE_ITEM_TYPE,
            fetched.map { it.id.toString() to it },
            QueueResource.serializer(),
            fetchedAtMillis = now(),
        )
        refreshStatus()
        ArrResult.ok(Unit)
    }

    /** Refresh only when the TTL expired; the steady-state path after the first load. */
    suspend fun refreshIfStale(ttlMillis: Long = QUEUE_TTL_MILLIS): ArrResult<Unit> =
        if (isStale(ttlMillis)) refresh() else ArrResult.ok(Unit)

    /** Header counts and error/warning flags; cache-first with its own TTL. */
    suspend fun status(ttlMillis: Long = QUEUE_TTL_MILLIS): ArrResult<QueueStatusResource> =
        withContext(ioContext) {
            val cached = store.getAll(serverId, QUEUE_STATUS_TYPE, QueueStatusResource.serializer())
            val fetchedAt = store.lastFetchedAt(serverId, QUEUE_STATUS_TYPE)
            if (fetchedAt != null && now() - fetchedAt <= ttlMillis && cached.isNotEmpty()) {
                return@withContext ArrResult.ok(cached.first())
            }
            when (val result = refreshStatus()) {
                is ArrResult.Ok -> ArrResult.ok(result.value ?: QueueStatusResource())
                is ArrResult.Err -> if (cached.isNotEmpty()) {
                    ArrResult.ok(cached.first())
                } else {
                    ArrResult.err(result.error)
                }
            }
        }

    /**
     * Removes one item. When offline, the removal applies optimistically to
     * the cache and the DELETE (plus any replacement search command) is
     * enqueued for replay.
     */
    suspend fun removeItem(
        itemId: Int,
        albumId: Int?,
        options: QueueRemoveOptions,
    ): ArrResult<QueueWriteOutcome> = withContext(ioContext) {
        val skipRedownload = if (options.blocklist) !options.searchReplacement else null
        try {
            queueApi.deleteQueue(
                id = itemId,
                removeFromClient = options.removeFromClient,
                blocklist = options.blocklist,
                skipRedownload = skipRedownload,
            )
        } catch (e: HttpException) {
            return@withContext enqueueRemoval(itemId, albumId, options, e.toArrError())
        } catch (e: IOException) {
            return@withContext enqueueRemoval(itemId, albumId, options, ArrError.Network(e))
        } catch (e: Throwable) {
            return@withContext enqueueRemoval(itemId, albumId, options, e.arrError())
        }
        store.delete(serverId, QUEUE_ITEM_TYPE, itemId.toString())
        if (!options.blocklist && options.searchReplacement && albumId != null) {
            searchAlbums(listOf(albumId))
        }
        ArrResult.ok(QueueWriteOutcome.Synced)
    }

    /**
     * Retries the import of a failed download by rescanning it server side
     * (`DownloadedAlbumsScan`). Offline, the command is enqueued. The runner
     * may fail by throwing (transport) or by returning an error result.
     */
    suspend fun retryImport(downloadId: String, outputPath: String?): ArrResult<QueueWriteOutcome> =
        withContext(ioContext) {
            val body = QueueCommandBody(
                name = LidarrQueueCommands.RETRY_IMPORT,
                downloadId = downloadId,
                path = outputPath,
            )
            val online = runCatching {
                commandRunner.run(body.name, body.downloadId, body.path, null)
            }.getOrElse { e ->
                ArrResult.err(e.toArrError())
            }
            if (online is ArrResult.Ok) {
                return@withContext ArrResult.ok(QueueWriteOutcome.Synced)
            }
            val error = (online as ArrResult.Err).error
            if (!error.isRetryable()) {
                return@withContext ArrResult.err(error)
            }
            val action = enqueueCommand(body)
            ArrResult.ok(QueueWriteOutcome.Queued(action.actionId))
        }

    /** Force-grabs a pending release; offline, the grab is enqueued. */
    suspend fun grab(itemId: Int): ArrResult<QueueWriteOutcome> = withContext(ioContext) {
        try {
            queueActionApi.grabQueue(itemId)
        } catch (e: HttpException) {
            return@withContext enqueueGrab(itemId, e.toArrError())
        } catch (e: IOException) {
            return@withContext enqueueGrab(itemId, ArrError.Network(e))
        } catch (e: Throwable) {
            return@withContext enqueueGrab(itemId, e.arrError())
        }
        ArrResult.ok(QueueWriteOutcome.Synced)
    }

    /**
     * Queued writes projected onto queue items: removals and grabs by item id,
     * retry imports by download id (the command body only knows the download).
     */
    fun queuedWrites(): Flow<QueuedQueueWrites> = actionQueue.observe().map { pending ->
        var writes = QueuedQueueWrites()
        pending.filter { it.serverId == serverId }.forEach { action ->
            when {
                action.method == DELETE_METHOD && action.path.startsWith(QUEUE_ITEM_PATH_PREFIX) ->
                    action.path.removePrefix(QUEUE_ITEM_PATH_PREFIX)
                        .substringBefore('?').toIntOrNull()
                        ?.let { id -> writes = writes.copy(removedItemIds = writes.removedItemIds + id) }

                action.method == POST_METHOD && action.path.startsWith(GRAB_PATH_PREFIX) ->
                    action.path.removePrefix(GRAB_PATH_PREFIX)
                        .substringBefore('?').toIntOrNull()
                        ?.let { id -> writes = writes.copy(grabbedItemIds = writes.grabbedItemIds + id) }

                action.method == POST_METHOD && action.path == COMMAND_PATH ->
                    action.bodyJson?.let { body ->
                        QueueCommandBody.decode(json, body)
                    }?.takeIf { it.name == LidarrQueueCommands.RETRY_IMPORT }
                        ?.downloadId
                        ?.let { id -> writes = writes.copy(retriedDownloadIds = writes.retriedDownloadIds + id) }
            }
        }
        writes
    }

    /** Queued action count for this server, for the pending banner. */
    fun queuedCount(): Flow<Int> = actionQueue.observe().map { pending ->
        pending.count { it.serverId == serverId }
    }

    /**
     * Combines the queued writes with the live items so retry commands can be
     * matched back to queue rows through their download id.
     */
    fun queuedItemIds(): Flow<Set<Int>> =
        combine(observeItems(), queuedWrites()) { items, writes ->
            writes.queuedItemIds(items)
        }

    private suspend fun refreshStatus(): ArrResult<QueueStatusResource?> = withContext(ioContext) {
        val fetched = try {
            queueStatusApi.getQueueStatus()
        } catch (e: HttpException) {
            return@withContext ArrResult.err(e.toArrError())
        } catch (e: IOException) {
            return@withContext ArrResult.err(ArrError.Network(e))
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrError())
        }
        store.put(
            serverId,
            QUEUE_STATUS_TYPE,
            STATUS_ENTITY_ID,
            fetched,
            QueueStatusResource.serializer(),
        )
        ArrResult.ok(fetched)
    }

    private suspend fun searchAlbums(albumIds: List<Int>): ArrResult<Unit> {
        val body = QueueCommandBody(name = LidarrQueueCommands.ALBUM_SEARCH, albumIds = albumIds)
        val result = runCatching {
            commandRunner.run(body.name, null, null, albumIds)
        }.getOrElse { e ->
            ArrResult.err(e.toArrError())
        }
        return if (result is ArrResult.Ok) result else ArrResult.err((result as ArrResult.Err).error)
    }

    private fun Throwable.toArrError(): ArrError = when (this) {
        is IOException -> ArrError.Network(this)
        else -> arrError()
    }

    private suspend fun enqueueRemoval(
        itemId: Int,
        albumId: Int?,
        options: QueueRemoveOptions,
        error: ArrError,
    ): ArrResult<QueueWriteOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        // Optimistic removal: the row disappears now, the DELETE replays later.
        store.delete(serverId, QUEUE_ITEM_TYPE, itemId.toString())
        val skipRedownload = if (options.blocklist) !options.searchReplacement else null
        val query = buildString {
            append("?removeFromClient=").append(options.removeFromClient)
            append("&blocklist=").append(options.blocklist)
            if (skipRedownload != null) append("&skipRedownload=").append(skipRedownload)
        }
        val removal = action(
            method = DELETE_METHOD,
            path = "$QUEUE_ITEM_PATH_PREFIX$itemId$query",
            bodyJson = null,
            description = "Remove queue item $itemId",
        )
        actionQueue.enqueue(removal)
        if (!options.blocklist && options.searchReplacement && albumId != null) {
            actionQueue.enqueue(
                action(
                    method = POST_METHOD,
                    path = COMMAND_PATH,
                    bodyJson = QueueCommandBody(
                        name = LidarrQueueCommands.ALBUM_SEARCH,
                        albumIds = listOf(albumId),
                    ).encode(json),
                    description = "Search album $albumId",
                ),
            )
        }
        return ArrResult.ok(QueueWriteOutcome.Queued(removal.actionId))
    }

    private suspend fun enqueueGrab(itemId: Int, error: ArrError): ArrResult<QueueWriteOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        val grab = action(
            method = POST_METHOD,
            path = "$GRAB_PATH_PREFIX$itemId",
            bodyJson = null,
            description = "Grab queue item $itemId",
        )
        actionQueue.enqueue(grab)
        return ArrResult.ok(QueueWriteOutcome.Queued(grab.actionId))
    }

    private suspend fun enqueueCommand(body: QueueCommandBody): QueuedAction = action(
        method = POST_METHOD,
        path = COMMAND_PATH,
        bodyJson = body.encode(json),
        description = "Command ${body.name}",
    ).also { actionQueue.enqueue(it) }

    private fun action(method: String, path: String, bodyJson: String?, description: String) =
        QueuedAction(
            actionId = ActionId(UUID.randomUUID().toString()),
            serverId = serverId,
            appType = AppType.LIDARR,
            method = method,
            path = path,
            bodyJson = bodyJson,
            description = description,
            enqueuedAtMillis = now(),
        )

    private fun ArrError.isRetryable(): Boolean =
        this is ArrError.Offline || this is ArrError.Network

    private fun HttpException.toArrError(): ArrError = when (code()) {
        401, 403 -> ArrError.Auth(code())
        else -> ArrError.Http(code(), message())
    }

    companion object {
        internal val QUEUE_ITEM_TYPE = EntityType("lidarr.queue.item")
        internal val QUEUE_STATUS_TYPE = EntityType("lidarr.queue.status")

        /** Fifteen seconds: queues move fast, and live events extend freshness. */
        const val QUEUE_TTL_MILLIS: Long = 15 * 1000

        internal const val STATUS_ENTITY_ID = "status"
        internal const val QUEUE_ITEM_PATH_PREFIX = "api/v1/queue/"
        internal const val GRAB_PATH_PREFIX = "api/v1/queue/grab/"
        internal const val COMMAND_PATH = "api/v1/command"
        private const val DELETE_METHOD = "DELETE"
        private const val POST_METHOD = "POST"
    }
}
