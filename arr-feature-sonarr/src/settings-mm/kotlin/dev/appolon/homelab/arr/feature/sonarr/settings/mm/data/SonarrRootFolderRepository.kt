package dev.appolon.homelab.arr.feature.sonarr.settings.mm.data

import dev.appolon.homelab.arr.api.sonarr.RootFolderApi
import dev.appolon.homelab.arr.api.sonarr.model.DiskSpaceResource
import dev.appolon.homelab.arr.api.sonarr.model.RootFolderResource
import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.QueuedAction
import dev.appolon.homelab.arr.feature.sonarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.net.ArrJson
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * One root folder joined with the disk that hosts it, when a matching
 * [DiskSpaceResource] exists. Sonarr reports free and total bytes on the
 * folder itself, so [usedFraction] prefers those and only falls back to
 * the joined disk when the folder reports no total (inaccessible mounts).
 */
data class RootFolderWithDisk(
    val folder: RootFolderResource,
    val disk: DiskSpaceResource?,
) {
    val unmappedCount: Int get() = folder.unmappedFolders?.size ?: 0

    val freeSpace: Long get() = host().freeSpace
    val totalSpace: Long get() = host().totalSpace

    /** Drives the free-space bar; null when neither source knows a total. */
    val usedFraction: Float?
        get() {
            val host = host()
            return host.takeIf { it.totalSpace > 0 }?.let {
                ((it.totalSpace - it.freeSpace).toDouble() / it.totalSpace).toFloat().coerceIn(0f, 1f)
            }
        }

    private fun host(): DiskSpaceLike =
        if (folder.totalSpace > 0 || disk == null) {
            DiskSpaceLike(folder.freeSpace, folder.totalSpace)
        } else {
            DiskSpaceLike(disk.freeSpace, disk.totalSpace)
        }

    private data class DiskSpaceLike(val freeSpace: Long, val totalSpace: Long)
}

/**
 * Root folder repository with the same cache-first shape as the config
 * repository: the list refreshes through the RootFolder endpoints, and
 * the free-space bars also read the DiskSpace endpoint so inaccessible
 * folders still show a total when the mount reports one. Add, remove,
 * and the path-move edit go to the server first and fall back to an
 * optimistic cache change plus a queued action when offline.
 */
class SonarrRootFolderRepository(
    private val serverId: ServerId,
    private val store: EntityStore,
    private val rootFolderApi: RootFolderApi,
    private val diskSpaceLoader: suspend () -> List<DiskSpaceResource>,
    private val actionQueue: ActionQueue,
    private val now: () -> Long = System::currentTimeMillis,
    private val ioContext: CoroutineContext = Dispatchers.IO,
) {

    /**
     * Bumped per enqueue so successive queued writes replay in the order
     * they were made even within one clock millisecond.
     */
    private var writeSequence = 0L

    fun observeFolders(): Flow<List<RootFolderResource>> =
        store.observe(serverId, ROOT_FOLDER_TYPE, RootFolderResource.serializer())

    /** Folders joined to their hosting disk for the free-space bars. */
    fun observeWithDisk(): Flow<List<RootFolderWithDisk>> =
        store.observe(serverId, ROOT_FOLDER_TYPE, RootFolderResource.serializer())
            // Single-flow read-time join rather than combine(flow, flow):
            // combine's internal collectors do not wake on later store
            // ticks under the ViewModel's Main.immediate dispatcher, so
            // the first (empty) join would stick. The disks are read at
            // each folders emission, and refresh writes disks first so
            // the folders emission that follows joins fresh totals.
            .map { folders -> joinDisks(folders, cachedDisks()) }

    suspend fun cachedFolders(): List<RootFolderResource> =
        store.getAll(serverId, ROOT_FOLDER_TYPE, RootFolderResource.serializer())

    /** Cached disk space rows, joined into [RootFolderWithDisk] at read time. */
    suspend fun cachedDisks(): List<DiskSpaceResource> =
        store.getAll(serverId, DISK_SPACE_TYPE, DiskSpaceResource.serializer())

    /** True when the folder list cache is absent or older than [ttlMillis]. */
    suspend fun isStale(ttlMillis: Long = ROOT_FOLDER_TTL_MILLIS): Boolean {
        val fetched = store.lastFetchedAt(serverId, ROOT_FOLDER_TYPE) ?: return true
        return now() - fetched > ttlMillis
    }

    /** Re-fetches folders and disk space, replacing both caches. */
    suspend fun refresh(): ArrResult<Unit> = withContext(ioContext) {
        val folders = when (val r = fetch { rootFolderApi.list() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val disks = when (val r = fetch { diskSpaceLoader() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        // Disks first: the read-time join in observeWithDisk runs on each
        // folders emission, so the folders write must see fresh totals.
        store.deleteAll(serverId, DISK_SPACE_TYPE)
        disks.forEachIndexed { index, disk ->
            store.put(serverId, DISK_SPACE_TYPE, index.toString(), disk, DiskSpaceResource.serializer())
        }
        store.deleteAll(serverId, ROOT_FOLDER_TYPE)
        store.putAll(
            serverId,
            ROOT_FOLDER_TYPE,
            folders.map { it.id.toString() to it },
            RootFolderResource.serializer(),
            fetchedAtMillis = now(),
        )
        ArrResult.ok(Unit)
    }

    /** Refresh only when the TTL expired. */
    suspend fun refreshIfStale(ttlMillis: Long = ROOT_FOLDER_TTL_MILLIS): ArrResult<Unit> =
        if (isStale(ttlMillis)) refresh() else ArrResult.ok(Unit)

    /**
     * Adds a root folder. Offline: the folder appears in the cache with the
     * next negative id (a client-side placeholder the replay reconciles)
     * and the POST is enqueued.
     */
    suspend fun add(path: String): ArrResult<WriteOutcome> = withContext(ioContext) {
        val body = RootFolderResource(path = path)
        try {
            val created = rootFolderApi.create(body)
            store.put(serverId, ROOT_FOLDER_TYPE, created.id.toString(), created, RootFolderResource.serializer())
            ArrResult.ok(WriteOutcome.Synced)
        } catch (e: IOException) {
            enqueue(
                method = POST_METHOD,
                path = ROOT_FOLDER_PATH,
                body = body,
                optimistic = { cacheAddPlaceholder(path) },
                description = "Add root folder $path",
                error = ArrError.Network(e),
            )
        } catch (t: Throwable) {
            ArrResult.err(t.arrErrorOrUnknown())
        }
    }

    /** Removes a root folder; offline clears the cached row and queues the DELETE. */
    suspend fun remove(folderId: Int): ArrResult<WriteOutcome> = withContext(ioContext) {
        try {
            rootFolderApi.delete(folderId)
            store.delete(serverId, ROOT_FOLDER_TYPE, folderId.toString())
            ArrResult.ok(WriteOutcome.Synced)
        } catch (e: IOException) {
            enqueue(
                method = DELETE_METHOD,
                path = "$ROOT_FOLDER_PATH/$folderId",
                body = null,
                optimistic = { store.delete(serverId, ROOT_FOLDER_TYPE, folderId.toString()) },
                description = "Remove root folder $folderId",
                error = ArrError.Network(e),
            )
        } catch (t: Throwable) {
            ArrResult.err(t.arrErrorOrUnknown())
        }
    }

    /**
     * "Edits" a root folder path. Sonarr's v3 API has no PUT for root
     * folders, so a path change is create-then-delete, executed in that
     * order so the queue replays it in the same order. When either half
     * lands offline both halves enqueue and the placeholder swap happens
     * in the cache.
     */
    suspend fun move(old: RootFolderResource, newPath: String): ArrResult<WriteOutcome> =
        withContext(ioContext) {
            val addOutcome = when (val r = add(newPath)) {
                is ArrResult.Err -> return@withContext r
                is ArrResult.Ok -> r.value
            }
            val removeOutcome = when (val r = remove(old.id)) {
                is ArrResult.Err -> return@withContext r
                is ArrResult.Ok -> r.value
            }
            val queued = (addOutcome as? WriteOutcome.Queued)
                ?: (removeOutcome as? WriteOutcome.Queued)
            ArrResult.ok(queued ?: WriteOutcome.Synced)
        }

    /** Folder ids with a queued removal, for per-row queued badges. */
    fun queuedRemovedIds(): Flow<Set<Int>> = actionQueue.observe().map { pending ->
        pending.asSequence()
            .filter { it.serverId == serverId && it.method == DELETE_METHOD && it.path.startsWith("$ROOT_FOLDER_PATH/") }
            .mapNotNull { it.path.removePrefix("$ROOT_FOLDER_PATH/").substringBefore('/').toIntOrNull() }
            .toSet()
    }

    /** Queued action count for this repository's writes. */
    fun queuedCount(): Flow<Int> = actionQueue.observe().map { pending ->
        pending.count { it.serverId == serverId && it.path.startsWith(ROOT_FOLDER_PATH) }
    }

    /**
     * Joins folders to disks by longest matching mount path. A disk matches
     * when its normalized path is a prefix of the folder's normalized path;
     * the longest match wins so nested mounts resolve to the deepest disk.
     */
    private fun joinDisks(
        folders: List<RootFolderResource>,
        disks: List<DiskSpaceResource>,
    ): List<RootFolderWithDisk> = folders.sortedBy { it.path }.map { folder ->
        val folderPath = (folder.path ?: "").normalized()
        val disk = disks
            .mapNotNull { disk ->
                disk.path?.normalized()
                    ?.takeIf { folderPath.startsWith(it) }
                    ?.let { mountPath -> mountPath to disk }
            }
            .maxByOrNull { (mountPath) -> mountPath.length }
            ?.second
        RootFolderWithDisk(folder, disk)
    }

    private fun String.normalized(): String =
        if (endsWith('/')) this else "$this/"

    private suspend fun cacheAddPlaceholder(path: String) {
        val placeholderId = -(cachedFolders().size + 1)
        store.put(
            serverId,
            ROOT_FOLDER_TYPE,
            placeholderId.toString(),
            RootFolderResource(id = placeholderId, path = path),
            RootFolderResource.serializer(),
        )
    }

    private suspend fun enqueue(
        method: String,
        path: String,
        body: RootFolderResource?,
        optimistic: suspend () -> Unit,
        description: String,
        error: ArrError,
    ): ArrResult<WriteOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        val action = QueuedAction(
            actionId = ActionId(UUID.randomUUID().toString()),
            serverId = serverId,
            appType = AppType.SONARR,
            method = method,
            path = path,
            bodyJson = body?.let { ArrJson.encodeToString(RootFolderResource.serializer(), it) },
            description = description,
            // The queue orders by timestamp and breaks ties on the random
            // action id, so rapid writes (move = create + delete) get a
            // strictly increasing stamp to keep their replay order.
            enqueuedAtMillis = now() + writeSequence++,
        )
        optimistic()
        actionQueue.enqueue(action)
        return ArrResult.ok(WriteOutcome.Queued(action.actionId))
    }

    private suspend fun <T> fetch(call: suspend () -> T): ArrResult<T> = try {
        ArrResult.ok(call())
    } catch (e: IOException) {
        ArrResult.err(ArrError.Network(e))
    } catch (e: Throwable) {
        ArrResult.err(e.arrErrorOrUnknown())
    }

    private fun ArrError.isRetryable(): Boolean =
        this is ArrError.Offline || this is ArrError.Network

    private fun Throwable.arrErrorOrUnknown(): ArrError = when (this) {
        is retrofit2.HttpException -> when (code()) {
            401, 403 -> ArrError.Auth(code())
            else -> ArrError.Http(code(), message())
        }

        else -> ArrError.Unknown(this)
    }

    companion object {
        internal val ROOT_FOLDER_TYPE = EntityType("sonarr.rootfolder")
        internal val DISK_SPACE_TYPE = EntityType("sonarr.diskspace")

        /** One minute, matching how often Sonarr's UI polls disk space. */
        const val ROOT_FOLDER_TTL_MILLIS: Long = 60 * 1000

        internal const val ROOT_FOLDER_PATH = "api/v3/rootfolder"
        private const val POST_METHOD = "POST"
        private const val DELETE_METHOD = "DELETE"
    }
}
