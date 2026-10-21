package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.BlocklistBulkResource
import dev.appolon.homelab.arr.api.lidarr.model.BlocklistResource
import dev.appolon.homelab.arr.api.lidarr.model.BlocklistResourcePagingResource
import dev.appolon.homelab.arr.api.lidarr.model.CommandResource
import dev.appolon.homelab.arr.api.lidarr.model.CommandStatus
import dev.appolon.homelab.arr.api.lidarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.lidarr.model.EntityHistoryEventType
import dev.appolon.homelab.arr.api.lidarr.model.HistoryResource
import dev.appolon.homelab.arr.api.lidarr.model.HistoryResourcePagingResource
import dev.appolon.homelab.arr.api.lidarr.model.ManualImportResource
import dev.appolon.homelab.arr.api.lidarr.model.ManualImportUpdateResource
import dev.appolon.homelab.arr.api.lidarr.model.ParseResource
import dev.appolon.homelab.arr.api.lidarr.model.PingResource
import dev.appolon.homelab.arr.api.lidarr.model.QueueBulkResource
import dev.appolon.homelab.arr.api.lidarr.model.QueueResource
import dev.appolon.homelab.arr.api.lidarr.model.QueueResourcePagingResource
import dev.appolon.homelab.arr.api.lidarr.model.QueueStatusResource
import dev.appolon.homelab.arr.api.lidarr.model.ReleaseResource
import dev.appolon.homelab.arr.api.lidarr.model.SortDirection
import dev.appolon.homelab.arr.api.lidarr.model.TrackedDownloadStatus
import java.time.Instant
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/**
 * In-memory implementations of all queue and release clients. Seed collections before UI tests.
 * [latency] can suspend on the test scheduler. [error] runs before reads and mutations.
 * [now] makes command and blocklist dates deterministic without parsing dates in DTOs.
 * This fake records download-client actions but never accesses files or download clients.
 */
class FakeLidarrQueue(
    var latency: suspend (operation: String) -> Unit = {},
    var error: (operation: String) -> Throwable? = { null },
    var now: () -> String = { Instant.now().toString() },
) : QueueApi, QueueActionApi, QueueDetailsApi, QueueStatusApi, BlocklistApi, HistoryApi,
    CommandApi, ManualImportApi, ReleaseApi, ReleasePushApi, ParseApi, PingApi {
    val queue: MutableMap<Int, QueueResource> = linkedMapOf()
    val blocklist: MutableMap<Int, BlocklistResource> = linkedMapOf()
    val history: MutableMap<Int, HistoryResource> = linkedMapOf()
    val commands: MutableMap<Int, CommandResource> = linkedMapOf()
    val manualImports: MutableMap<Int, ManualImportResource> = linkedMapOf()
    val releases: MutableMap<Int, ReleaseResource> = linkedMapOf()
    val parsedTitles: MutableMap<String, ParseResource> = linkedMapOf()
    val grabbedQueueIds: MutableList<Int> = mutableListOf()
    val grabbedReleases: MutableList<ReleaseResource> = mutableListOf()
    val pushedReleases: MutableList<ReleaseResource> = mutableListOf()
    val removedFromClient: MutableSet<Int> = linkedSetOf()
    val skippedRedownload: MutableSet<Int> = linkedSetOf()
    val changedCategory: MutableSet<Int> = linkedSetOf()
    val failedHistoryIds: MutableSet<Int> = linkedSetOf()
    val manualImportUpdates: MutableList<ManualImportUpdateResource> = mutableListOf()

    /** Numeric event filters have no mapping in the spec. Tests can supply the server's mapping. */
    val historyEventTypeIds: MutableMap<EntityHistoryEventType, Int> = linkedMapOf()
    var pingResource: PingResource = PingResource("OK")
    var headPingStatus: Int = 200

    private suspend fun before(operation: String) {
        latency(operation)
        error(operation)?.let { throw it }
    }

    private fun missing(): Nothing = throw HttpException(
        Response.error<Unit>(404, "{}".toResponseBody("application/json".toMediaType()))
    )

    private fun nextId(ids: Set<Int>): Int = (ids.maxOrNull() ?: 0) + 1

    private fun <T> page(items: List<T>, page: Int?, size: Int?): List<T> {
        val start = ((page ?: 1).coerceAtLeast(1).toLong() - 1) * (size ?: 10).coerceAtLeast(1)
        return items.drop(start.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            .take((size ?: 10).coerceAtLeast(1))
    }

    private fun <T> sorted(
        items: List<T>,
        direction: SortDirection?,
        key: (T) -> Comparable<*>?,
    ): List<T> {
        val ordered = items.sortedWith { a, b ->
            @Suppress("UNCHECKED_CAST")
            compareValues(key(a) as Comparable<Any>?, key(b) as Comparable<Any>?)
        }
        return if (direction == SortDirection.DESCENDING) ordered.reversed() else ordered
    }

    override suspend fun getQueue(
        page: Int?, pageSize: Int?, sortKey: String?, sortDirection: SortDirection?,
        includeUnknownArtistItems: Boolean?, includeArtist: Boolean?, includeAlbum: Boolean?,
        artistIds: List<Int>?, protocol: DownloadProtocol?, quality: List<Int>?,
    ): QueueResourcePagingResource {
        before("getQueue")
        val matches = sorted(queue.values.filter {
            (includeUnknownArtistItems == true || it.artistId != null) &&
                (artistIds == null || it.artistId in artistIds) &&
                (protocol == null || it.protocol == protocol) &&
                (quality == null || it.quality?.quality?.id in quality)
        }, sortDirection) {
            when (sortKey) {
                "title" -> it.title
                "size" -> it.size
                "sizeleft" -> it.sizeleft
                "timeleft" -> it.timeleft
                "added" -> it.added
                else -> it.id
            }
        }
        return QueueResourcePagingResource(
            page ?: 1, pageSize ?: 10, sortKey, sortDirection ?: SortDirection.DEFAULT,
            matches.size, page(matches, page, pageSize).map {
                it.copy(
                    artist = it.artist.takeIf { includeArtist == true },
                    album = it.album.takeIf { includeAlbum == true },
                )
            },
        )
    }

    private fun removeQueue(
        id: Int, removeFromClient: Boolean?, addBlocklist: Boolean?,
        skipRedownload: Boolean?, changeCategory: Boolean?,
    ) {
        val item = queue.remove(id) ?: missing()
        if (removeFromClient != false) removedFromClient.add(id)
        if (skipRedownload == true) skippedRedownload.add(id)
        if (changeCategory == true) changedCategory.add(id)
        if (addBlocklist == true) {
            val key = nextId(blocklist.keys)
            blocklist[key] = BlocklistResource(
                id = key, artistId = item.artistId ?: 0,
                albumIds = item.albumId?.let { listOf(it) }, sourceTitle = item.title,
                quality = item.quality, customFormats = item.customFormats, date = now(),
                protocol = item.protocol, indexer = item.indexer, artist = item.artist,
            )
        }
    }

    override suspend fun deleteQueue(
        id: Int, removeFromClient: Boolean?, blocklist: Boolean?,
        skipRedownload: Boolean?, changeCategory: Boolean?,
    ) {
        before("deleteQueue")
        removeQueue(id, removeFromClient, blocklist, skipRedownload, changeCategory)
    }

    override suspend fun deleteQueueBulk(
        body: QueueBulkResource, removeFromClient: Boolean?, blocklist: Boolean?,
        skipRedownload: Boolean?, changeCategory: Boolean?,
    ) {
        before("deleteQueueBulk")
        val ids = body.ids.orEmpty().distinct()
        if (ids.any { it !in queue }) missing()
        ids.forEach { removeQueue(it, removeFromClient, blocklist, skipRedownload, changeCategory) }
    }

    override suspend fun grabQueue(id: Int) {
        before("grabQueue")
        val item = queue[id] ?: missing()
        queue[id] = item.copy(downloadForced = true)
        grabbedQueueIds.add(id)
    }

    override suspend fun grabQueueBulk(body: QueueBulkResource) {
        before("grabQueueBulk")
        val ids = body.ids.orEmpty().distinct()
        if (ids.any { it !in queue }) missing()
        ids.forEach { id ->
            queue[id] = queue.getValue(id).copy(downloadForced = true)
            grabbedQueueIds.add(id)
        }
    }

    override suspend fun getQueueDetails(
        artistId: Int?, albumIds: List<Int>?, includeArtist: Boolean?, includeAlbum: Boolean?,
    ): List<QueueResource> {
        before("getQueueDetails")
        return queue.values.filter {
            (artistId == null || it.artistId == artistId) &&
                (albumIds == null || it.albumId in albumIds)
        }.map {
            it.copy(
                artist = it.artist.takeIf { includeArtist == true },
                album = it.album.takeIf { includeAlbum != false },
            )
        }
    }

    override suspend fun getQueueStatus(): QueueStatusResource {
        before("getQueueStatus")
        val known = queue.values.filter { it.artistId != null }
        val unknown = queue.values.filter { it.artistId == null }
        return QueueStatusResource(
            totalCount = queue.size, count = known.size, unknownCount = unknown.size,
            errors = known.any { it.trackedDownloadStatus == TrackedDownloadStatus.ERROR },
            warnings = known.any { it.trackedDownloadStatus == TrackedDownloadStatus.WARNING },
            unknownErrors = unknown.any { it.trackedDownloadStatus == TrackedDownloadStatus.ERROR },
            unknownWarnings = unknown.any { it.trackedDownloadStatus == TrackedDownloadStatus.WARNING },
        )
    }

    override suspend fun getBlocklist(
        page: Int?, pageSize: Int?, sortKey: String?, sortDirection: SortDirection?,
    ): BlocklistResourcePagingResource {
        before("getBlocklist")
        val matches = sorted(blocklist.values.toList(), sortDirection) {
            when (sortKey) {
                "date" -> it.date
                "sourceTitle" -> it.sourceTitle
                "artistId" -> it.artistId
                else -> it.id
            }
        }
        return BlocklistResourcePagingResource(
            page ?: 1, pageSize ?: 10, sortKey, sortDirection ?: SortDirection.DEFAULT,
            matches.size, page(matches, page, pageSize),
        )
    }

    override suspend fun deleteBlocklist(id: Int) {
        before("deleteBlocklist")
        if (blocklist.remove(id) == null) missing()
    }

    override suspend fun deleteBlocklistBulk(body: BlocklistBulkResource) {
        before("deleteBlocklistBulk")
        val ids = body.ids.orEmpty().distinct()
        if (ids.any { it !in blocklist }) missing()
        ids.forEach { blocklist.remove(it) }
    }

    private fun HistoryResource.includes(
        includeArtist: Boolean?, includeAlbum: Boolean?, includeTrack: Boolean?,
    ): HistoryResource = copy(
        artist = artist.takeIf { includeArtist == true },
        album = album.takeIf { includeAlbum == true },
        track = track.takeIf { includeTrack == true },
    )

    override suspend fun getHistory(
        page: Int?, pageSize: Int?, sortKey: String?, sortDirection: SortDirection?,
        includeArtist: Boolean?, includeAlbum: Boolean?, includeTrack: Boolean?,
        eventType: List<Int>?, albumId: Int?, downloadId: String?, artistIds: List<Int>?,
        quality: List<Int>?,
    ): HistoryResourcePagingResource {
        before("getHistory")
        val matches = sorted(history.values.filter {
            (eventType == null || historyEventTypeIds[it.eventType] in eventType) &&
                (albumId == null || it.albumId == albumId) &&
                (downloadId == null || it.downloadId == downloadId) &&
                (artistIds == null || it.artistId in artistIds) &&
                (quality == null || it.quality?.quality?.id in quality)
        }, sortDirection) {
            when (sortKey) {
                "date" -> it.date
                "sourceTitle" -> it.sourceTitle
                "artistId" -> it.artistId
                "albumId" -> it.albumId
                else -> it.id
            }
        }
        return HistoryResourcePagingResource(
            page ?: 1, pageSize ?: 10, sortKey, sortDirection ?: SortDirection.DEFAULT,
            matches.size, page(matches, page, pageSize).map {
                it.includes(includeArtist, includeAlbum, includeTrack)
            },
        )
    }

    override suspend fun getHistorySince(
        date: String?, eventType: EntityHistoryEventType?,
        includeArtist: Boolean?, includeAlbum: Boolean?, includeTrack: Boolean?,
    ): List<HistoryResource> {
        before("getHistorySince")
        val since = date?.let(Instant::parse)
        return history.values.filter {
            (since == null || Instant.parse(it.date).isAfter(since)) &&
                (eventType == null || it.eventType == eventType)
        }.map { it.includes(includeArtist, includeAlbum, includeTrack) }
    }

    override suspend fun getArtistHistory(
        artistId: Int?, albumId: Int?, eventType: EntityHistoryEventType?,
        includeArtist: Boolean?, includeAlbum: Boolean?, includeTrack: Boolean?,
    ): List<HistoryResource> {
        before("getArtistHistory")
        return history.values.filter {
            (artistId == null || it.artistId == artistId) &&
                (albumId == null || it.albumId == albumId) &&
                (eventType == null || it.eventType == eventType)
        }.map { it.includes(includeArtist, includeAlbum, includeTrack) }
    }

    override suspend fun failHistory(id: Int) {
        before("failHistory")
        val item = history[id] ?: missing()
        history[id] = item.copy(eventType = EntityHistoryEventType.DOWNLOAD_FAILED)
        failedHistoryIds.add(id)
    }

    override suspend fun getCommand(id: Int): CommandResource {
        before("getCommand")
        return commands[id] ?: missing()
    }

    override suspend fun deleteCommand(id: Int) {
        before("deleteCommand")
        val item = commands[id] ?: missing()
        commands[id] = item.copy(status = CommandStatus.CANCELLED)
    }

    override suspend fun addCommand(body: CommandResource): CommandResource {
        before("addCommand")
        val value = body.copy(
            id = nextId(commands.keys), queued = now(), status = CommandStatus.QUEUED,
        )
        commands[value.id] = value
        return value
    }

    override suspend fun getCommands(): List<CommandResource> {
        before("getCommands")
        return commands.values.toList()
    }

    override suspend fun updateManualImport(body: List<ManualImportUpdateResource>) {
        before("updateManualImport")
        if (body.any { it.id !in manualImports }) missing()
        body.forEach { update ->
            val item = manualImports.getValue(update.id)
            manualImports[update.id] = item.copy(
                path = update.path ?: item.path, name = update.name ?: item.name,
                artist = update.artistId?.let { ArtistResource(id = it) } ?: item.artist,
                album = update.albumId?.let { AlbumResource(id = it) } ?: item.album,
                albumReleaseId = update.albumReleaseId ?: item.albumReleaseId,
                tracks = update.tracks ?: item.tracks?.filter {
                    update.trackIds == null || it.id in update.trackIds
                },
                quality = update.quality ?: item.quality, releaseGroup = update.releaseGroup,
                indexerFlags = update.indexerFlags, downloadId = update.downloadId,
                additionalFile = update.additionalFile,
                replaceExistingFiles = update.replaceExistingFiles,
                disableReleaseSwitching = update.disableReleaseSwitching,
                rejections = update.rejections,
            )
            manualImportUpdates.add(update)
        }
    }

    override suspend fun getManualImport(
        folder: String?, downloadId: String?, artistId: Int?,
        filterExistingFiles: Boolean?, replaceExistingFiles: Boolean?,
    ): List<ManualImportResource> {
        before("getManualImport")
        return manualImports.values.filter {
            (folder == null || it.path == folder || it.path?.startsWith("${folder.trimEnd('/')}/") == true) &&
                (downloadId == null || it.downloadId == downloadId) &&
                (artistId == null || it.artist?.id == artistId) &&
                (filterExistingFiles == false || it.tracks.orEmpty().none { track -> track.hasFile })
        }.map { it.copy(replaceExistingFiles = replaceExistingFiles != false) }
    }

    override suspend fun grabRelease(body: ReleaseResource): ReleaseResource {
        before("grabRelease")
        grabbedReleases.add(body)
        return body
    }

    override suspend fun getReleases(albumId: Int?, artistId: Int?): List<ReleaseResource> {
        before("getReleases")
        return releases.values.filter {
            (albumId == null || it.albumId == albumId) &&
                (artistId == null || it.artistId == artistId)
        }
    }

    override suspend fun pushRelease(body: ReleaseResource): ReleaseResource {
        before("pushRelease")
        val value = body.copy(id = body.id.takeIf { it != 0 } ?: nextId(releases.keys))
        releases[value.id] = value
        pushedReleases.add(value)
        return value
    }

    override suspend fun parse(title: String?): ParseResource {
        before("parse")
        return parsedTitles[title.orEmpty()] ?: ParseResource(title = title)
    }

    override suspend fun ping(): PingResource {
        before("ping")
        return pingResource
    }

    override suspend fun headPing(): Response<Void> {
        before("headPing")
        return if (headPingStatus in 200..299) Response.success<Void>(headPingStatus, null)
        else Response.error(headPingStatus, "".toResponseBody())
    }
}
