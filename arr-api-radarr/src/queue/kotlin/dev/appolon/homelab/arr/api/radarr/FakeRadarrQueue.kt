package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.BlocklistBulkResource
import dev.appolon.homelab.arr.api.radarr.model.BlocklistResource
import dev.appolon.homelab.arr.api.radarr.model.BlocklistResourcePagingResource
import dev.appolon.homelab.arr.api.radarr.model.CommandResource
import dev.appolon.homelab.arr.api.radarr.model.CommandStatus
import dev.appolon.homelab.arr.api.radarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.radarr.model.HistoryResource
import dev.appolon.homelab.arr.api.radarr.model.HistoryResourcePagingResource
import dev.appolon.homelab.arr.api.radarr.model.ManualImportReprocessResource
import dev.appolon.homelab.arr.api.radarr.model.ManualImportResource
import dev.appolon.homelab.arr.api.radarr.model.MovieHistoryEventType
import dev.appolon.homelab.arr.api.radarr.model.QueueBulkResource
import dev.appolon.homelab.arr.api.radarr.model.QueueResource
import dev.appolon.homelab.arr.api.radarr.model.QueueResourcePagingResource
import dev.appolon.homelab.arr.api.radarr.model.QueueStatus
import dev.appolon.homelab.arr.api.radarr.model.QueueStatusResource
import dev.appolon.homelab.arr.api.radarr.model.SortDirection
import dev.appolon.homelab.arr.api.radarr.model.TrackedDownloadStatus
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.time.Instant

/**
 * Mutable single-test state implementing every queue-group interface.
 * Inject a suspending [sleep] such as coroutine delay to use virtual time without a runtime
 * coroutine dependency. The default sleep blocks the calling thread, so UI tests should inject it.
 * This fake does not contact download clients, indexers, or the filesystem.
 * [errorFor] receives the operation name and runs before any state mutation.
 */
class FakeRadarrQueue(
    var latencyMs: Long = 0,
    var errorFor: (String) -> Throwable? = { null },
    private val sleep: suspend (Long) -> Unit = { Thread.sleep(it) },
    private val now: () -> String = { Instant.now().toString() },
    /** The spec uses integer history filters but does not define their enum-to-integer mapping. */
    var historyEventIds: Map<MovieHistoryEventType, Int> = emptyMap(),
) : QueueApi, QueueActionApi, QueueDetailsApi, QueueStatusApi, BlocklistApi,
    HistoryApi, ManualImportApi, CommandApi {
    val queueItems = mutableListOf<QueueResource>()
    val blocklistItems = mutableListOf<BlocklistResource>()
    val historyItems = mutableListOf<HistoryResource>()
    val importItems = mutableListOf<ManualImportResource>()
    val commandItems = mutableListOf<CommandResource>()

    /** Queue actions are recorded because there is no real download client in this fake. */
    val grabbedIds = mutableListOf<Int>()
    val removals = mutableListOf<Removal>()

    /** Effective server defaults are recorded when the caller leaves a flag absent. */
    data class Removal(
        val ids: List<Int>,
        val removeFromClient: Boolean,
        val blocklist: Boolean,
        val skipRedownload: Boolean,
        val changeCategory: Boolean,
    )

    private suspend fun before(operation: String) {
        require(latencyMs >= 0) { "Latency must be nonnegative" }
        if (latencyMs > 0) sleep(latencyMs)
        errorFor(operation)?.let { throw it }
    }

    private fun <T> notFound(): Response<T> = Response.error(404, "{}".toResponseBody())

    private fun emptyObject(): JsonObject = JsonObject(emptyMap())

    private fun remove(
        ids: List<Int>,
        removeFromClient: Boolean?,
        blocklist: Boolean?,
        skipRedownload: Boolean?,
        changeCategory: Boolean?,
    ) {
        removals += Removal(ids.toList(), removeFromClient ?: true, blocklist ?: false,
            skipRedownload ?: false, changeCategory ?: false)
        if (blocklist == true) {
            queueItems.filter { it.id in ids }.forEach { item ->
                blocklistItems += BlocklistResource(
                    id = (blocklistItems.maxOfOrNull { it.id } ?: 0) + 1,
                    movieId = item.movieId ?: 0,
                    sourceTitle = item.title,
                    languages = item.languages,
                    quality = item.quality,
                    customFormats = item.customFormats,
                    date = now(),
                    protocol = item.protocol,
                    indexer = item.indexer,
                    movie = item.movie,
                )
            }
        }
        queueItems.removeAll { it.id in ids }
    }

    override suspend fun removeQueue(
        id: Int,
        removeFromClient: Boolean?,
        blocklist: Boolean?,
        skipRedownload: Boolean?,
        changeCategory: Boolean?,
    ): Response<Unit> {
        before("removeQueue")
        if (queueItems.none { it.id == id }) return notFound()
        remove(listOf(id), removeFromClient, blocklist, skipRedownload, changeCategory)
        return Response.success(Unit)
    }

    override suspend fun removeQueueBulk(
        removeFromClient: Boolean?,
        blocklist: Boolean?,
        skipRedownload: Boolean?,
        changeCategory: Boolean?,
        body: QueueBulkResource,
    ): JsonObject {
        before("removeQueueBulk")
        remove(body.ids.orEmpty(), removeFromClient, blocklist, skipRedownload, changeCategory)
        return emptyObject()
    }

    override suspend fun queue(
        page: Int?,
        pageSize: Int?,
        sortKey: String?,
        sortDirection: SortDirection?,
        includeUnknownMovieItems: Boolean?,
        includeMovie: Boolean?,
        movieIds: List<Int>?,
        protocol: DownloadProtocol?,
        languages: List<Int>?,
        quality: List<Int>?,
        status: List<QueueStatus>?,
    ): QueueResourcePagingResource {
        before("queue")
        val filtered = queueItems.filter { item ->
            (includeUnknownMovieItems == true || item.movieId != null) &&
                (movieIds == null || item.movieId in movieIds) &&
                (protocol == null || item.protocol == protocol) &&
                (languages == null || item.languages.orEmpty().any { it.id in languages }) &&
                (quality == null || item.quality.quality?.id in quality) &&
                (status == null || item.status in status)
        }
        val sorted = ordered(filtered, sortKey, sortDirection) {
            ArrJson.encodeToJsonElement(QueueResource.serializer(), it) as JsonObject
        }
        return QueueResourcePagingResource(
            page = page ?: 1,
            pageSize = pageSize ?: 10,
            sortKey = sortKey,
            sortDirection = sortDirection ?: SortDirection.DEFAULT,
            totalRecords = filtered.size,
            records = paged(sorted, page, pageSize),
        )
    }

    override suspend fun grabQueue(id: Int): Response<JsonObject> {
        before("grabQueue")
        if (queueItems.none { it.id == id }) return notFound()
        grabbedIds += id
        return Response.success(emptyObject())
    }

    override suspend fun grabQueueBulk(body: QueueBulkResource): Response<JsonObject> {
        before("grabQueueBulk")
        if (body.ids.orEmpty().any { id -> queueItems.none { it.id == id } }) return notFound()
        grabbedIds += body.ids.orEmpty()
        return Response.success(emptyObject())
    }

    override suspend fun queueDetails(movieId: Int?, includeMovie: Boolean?): List<QueueResource> {
        before("queueDetails")
        return queueItems.filter { movieId == null || it.movieId == movieId }
    }

    override suspend fun queueStatus(): QueueStatusResource {
        before("queueStatus")
        val known = queueItems.filter { it.movieId != null }
        val unknown = queueItems.filter { it.movieId == null }
        return QueueStatusResource(
            totalCount = queueItems.size,
            count = known.size,
            unknownCount = unknown.size,
            errors = known.any { it.trackedDownloadStatus == TrackedDownloadStatus.ERROR },
            warnings = known.any { it.trackedDownloadStatus == TrackedDownloadStatus.WARNING },
            unknownErrors = unknown.any {
                it.trackedDownloadStatus == TrackedDownloadStatus.ERROR
            },
            unknownWarnings = unknown.any {
                it.trackedDownloadStatus == TrackedDownloadStatus.WARNING
            },
        )
    }

    override suspend fun blocklist(
        page: Int?,
        pageSize: Int?,
        sortKey: String?,
        sortDirection: SortDirection?,
        movieIds: List<Int>?,
        protocols: List<DownloadProtocol>?,
    ): BlocklistResourcePagingResource {
        before("blocklist")
        val filtered = blocklistItems.filter {
            (movieIds == null || it.movieId in movieIds) &&
                (protocols == null || it.protocol in protocols)
        }
        val sorted = ordered(filtered, sortKey, sortDirection) {
            ArrJson.encodeToJsonElement(BlocklistResource.serializer(), it) as JsonObject
        }
        return BlocklistResourcePagingResource(
            page = page ?: 1,
            pageSize = pageSize ?: 10,
            sortKey = sortKey,
            sortDirection = sortDirection ?: SortDirection.DEFAULT,
            totalRecords = filtered.size,
            records = paged(sorted, page, pageSize),
        )
    }

    override suspend fun movieBlocklist(movieId: Int?): List<BlocklistResource> {
        before("movieBlocklist")
        return blocklistItems.filter { movieId == null || it.movieId == movieId }
    }

    override suspend fun removeBlocklist(id: Int): Response<Unit> {
        before("removeBlocklist")
        blocklistItems.removeAll { it.id == id }
        return Response.success(Unit)
    }

    override suspend fun removeBlocklistBulk(body: BlocklistBulkResource): JsonObject {
        before("removeBlocklistBulk")
        blocklistItems.removeAll { it.id in body.ids.orEmpty() }
        return emptyObject()
    }

    override suspend fun history(
        page: Int?,
        pageSize: Int?,
        sortKey: String?,
        sortDirection: SortDirection?,
        includeMovie: Boolean?,
        eventType: List<Int>?,
        downloadId: String?,
        movieIds: List<Int>?,
        languages: List<Int>?,
        quality: List<Int>?,
    ): HistoryResourcePagingResource {
        before("history")
        val filtered = historyItems.filter { item ->
            (eventType == null || historyEventIds[item.eventType] in eventType) &&
                (downloadId == null || item.downloadId == downloadId) &&
                (movieIds == null || item.movieId in movieIds) &&
                (languages == null || item.languages.orEmpty().any { it.id in languages }) &&
                (quality == null || item.quality.quality?.id in quality)
        }
        val sorted = ordered(filtered, sortKey, sortDirection) {
            ArrJson.encodeToJsonElement(HistoryResource.serializer(), it) as JsonObject
        }
        return HistoryResourcePagingResource(
            page = page ?: 1,
            pageSize = pageSize ?: 10,
            sortKey = sortKey,
            sortDirection = sortDirection ?: SortDirection.DEFAULT,
            totalRecords = filtered.size,
            records = paged(sorted, page, pageSize),
        )
    }

    override suspend fun historySince(
        date: String?,
        eventType: MovieHistoryEventType?,
        includeMovie: Boolean?,
    ): List<HistoryResource> {
        before("historySince")
        val since = date?.let(Instant::parse)
        return historyItems.filter {
            (since == null || Instant.parse(it.date) >= since) &&
                (eventType == null || it.eventType == eventType)
        }
    }

    override suspend fun movieHistory(
        movieId: Int?,
        eventType: MovieHistoryEventType?,
        includeMovie: Boolean?,
    ): List<HistoryResource> {
        before("movieHistory")
        return historyItems.filter {
            (movieId == null || it.movieId == movieId) &&
                (eventType == null || it.eventType == eventType)
        }
    }

    override suspend fun markHistoryFailed(id: Int): JsonObject {
        before("markHistoryFailed")
        val index = historyItems.indexOfFirst { it.id == id }
        if (index < 0) throw HttpException(notFound<Unit>())
        historyItems[index] = historyItems[index].copy(
            eventType = MovieHistoryEventType.DOWNLOAD_FAILED,
        )
        return emptyObject()
    }

    override suspend fun manualImport(
        folder: String?,
        downloadId: String?,
        movieId: Int?,
        filterExistingFiles: Boolean?,
    ): List<ManualImportResource> {
        before("manualImport")
        return importItems.filter {
            (folder == null || it.path?.startsWith(folder.trimEnd('/') + "/") == true) &&
                (downloadId == null || it.downloadId == downloadId) &&
                (movieId == null || it.movie.id == movieId) &&
                (filterExistingFiles == false || it.movieFileId == null)
        }
    }

    override suspend fun reprocessManualImport(
        body: List<ManualImportReprocessResource>,
    ): Response<List<ManualImportReprocessResource>> {
        before("reprocessManualImport")
        for (item in body) {
            val index = importItems.indexOfFirst { it.id == item.id }
            if (index >= 0) {
                importItems[index] = importItems[index].copy(
                    path = item.path,
                    movie = item.movie,
                    quality = item.quality,
                    languages = item.languages,
                    releaseGroup = item.releaseGroup,
                    downloadId = item.downloadId,
                    customFormats = item.customFormats,
                    customFormatScore = item.customFormatScore,
                    indexerFlags = item.indexerFlags,
                    rejections = item.rejections,
                )
            }
        }
        return Response.success(body.toList())
    }

    override suspend fun startCommand(body: CommandResource): Response<CommandResource> {
        before("startCommand")
        val command = body.copy(
            id = (commandItems.maxOfOrNull { it.id } ?: 0) + 1,
            status = CommandStatus.QUEUED,
            queued = now(),
        )
        commandItems += command
        return Response.success(201, command)
    }

    override suspend fun commands(): List<CommandResource> {
        before("commands")
        return commandItems.toList()
    }

    override suspend fun command(id: Int): Response<CommandResource> {
        before("command")
        return commandItems.firstOrNull { it.id == id }?.let { Response.success(it) } ?: notFound()
    }

    override suspend fun cancelCommand(id: Int): Response<Unit> {
        before("cancelCommand")
        val index = commandItems.indexOfFirst { it.id == id }
        if (index >= 0) {
            commandItems[index] = commandItems[index].copy(status = CommandStatus.CANCELLED)
        }
        return Response.success(Unit)
    }

    private fun <T> paged(items: List<T>, page: Int?, pageSize: Int?): List<T> {
        val p = page ?: 1
        val size = pageSize ?: 10
        require(p >= 1 && size >= 1) { "Page and page size must be positive" }
        val start = ((p.toLong() - 1) * size).coerceAtMost(items.size.toLong()).toInt()
        return items.drop(start).take(size)
    }

    private fun <T> ordered(
        items: List<T>,
        key: String?,
        direction: SortDirection?,
        json: (T) -> JsonObject,
    ): List<T> {
        if (key == null) return items.toList()
        val sorted = items.sortedWith { a, b ->
            val left = json(a)[key]?.jsonPrimitive?.content.orEmpty()
            val right = json(b)[key]?.jsonPrimitive?.content.orEmpty()
            val ln = left.toDoubleOrNull()
            val rn = right.toDoubleOrNull()
            if (ln != null && rn != null) ln.compareTo(rn) else left.compareTo(right)
        }
        return if (direction == SortDirection.DESCENDING) sorted.reversed() else sorted
    }

}
