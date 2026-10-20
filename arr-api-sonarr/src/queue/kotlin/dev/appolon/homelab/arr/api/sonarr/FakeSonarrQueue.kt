package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.BlocklistBulkResource
import dev.appolon.homelab.arr.api.sonarr.model.BlocklistResource
import dev.appolon.homelab.arr.api.sonarr.model.BlocklistResourcePagingResource
import dev.appolon.homelab.arr.api.sonarr.model.Command
import dev.appolon.homelab.arr.api.sonarr.model.CommandResource
import dev.appolon.homelab.arr.api.sonarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeHistoryEventType
import dev.appolon.homelab.arr.api.sonarr.model.HistoryResource
import dev.appolon.homelab.arr.api.sonarr.model.HistoryResourcePagingResource
import dev.appolon.homelab.arr.api.sonarr.model.ManualImportReprocessResource
import dev.appolon.homelab.arr.api.sonarr.model.ManualImportResource
import dev.appolon.homelab.arr.api.sonarr.model.ParseResource
import dev.appolon.homelab.arr.api.sonarr.model.PingResource
import dev.appolon.homelab.arr.api.sonarr.model.QueueBulkResource
import dev.appolon.homelab.arr.api.sonarr.model.QueueResource
import dev.appolon.homelab.arr.api.sonarr.model.QueueResourcePagingResource
import dev.appolon.homelab.arr.api.sonarr.model.QueueStatus
import dev.appolon.homelab.arr.api.sonarr.model.QueueStatusResource
import dev.appolon.homelab.arr.api.sonarr.model.ReleaseResource
import dev.appolon.homelab.arr.api.sonarr.model.SortDirection
import dev.appolon.homelab.arr.api.sonarr.model.TrackedDownloadStatus
import dev.appolon.homelab.arr.net.ArrJson
import java.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/**
 * Mutable single-test state for all queue-group interfaces.
 * [latency] runs before [error], which can fail any operation before it mutates state.
 * Callbacks receive `Tag.method`. Projections retain seeded embeddings. Removal flags
 * are accepted without simulating an external download client or a filesystem.
 */
class FakeSonarrQueue(
    var latency: suspend (String) -> Unit = {},
    var error: (String) -> Throwable? = { null }
) {
    val queueStore: MutableMap<Int, QueueResource> = linkedMapOf()
    val blocklistStore: MutableMap<Int, BlocklistResource> = linkedMapOf()
    val historyStore: MutableMap<Int, HistoryResource> = linkedMapOf()
    val commandStore: MutableMap<Int, CommandResource> = linkedMapOf()
    val manualImports: MutableList<ManualImportResource> = mutableListOf()
    val reprocessedImports: MutableList<ManualImportReprocessResource> = mutableListOf()
    val releases: MutableList<ReleaseResource> = mutableListOf()
    val grabbedReleases: MutableList<ReleaseResource> = mutableListOf()
    val pushedReleases: MutableList<ReleaseResource> = mutableListOf()
    val grabbedQueueIds: MutableList<Int> = mutableListOf()
    val parsedTitles: MutableMap<String, ParseResource> = linkedMapOf()
    var pingResource: PingResource = PingResource(status = "OK")
    var pingStatusCode: Int = 200

    private suspend fun before(operation: String) {
        latency(operation)
        error(operation)?.let { throw it }
    }

    private fun missing(id: Int): Nothing = throw HttpException(
        Response.error<Any>(404, "Missing resource $id".toResponseBody())
    )

    private fun <T> paged(rows: List<T>, page: Int?, pageSize: Int?): List<T> {
        val size = (pageSize ?: 10).coerceAtLeast(0)
        val start = (((page ?: 1).coerceAtLeast(1) - 1).toLong() * size)
            .coerceAtMost(rows.size.toLong()).toInt()
        return rows.drop(start).take(size)
    }

    private inline fun <reified T> sorted(
        rows: List<T>, key: String?, direction: SortDirection?
    ): List<T> {
        if (key == null) return rows
        val result = rows.sortedWith { left, right ->
            val a = (ArrJson.encodeToJsonElement(left) as JsonObject)[key]
            val b = (ArrJson.encodeToJsonElement(right) as JsonObject)[key]
            val x = a?.toString()?.trim('"').orEmpty()
            val y = b?.toString()?.trim('"').orEmpty()
            val xn = x.toDoubleOrNull()
            val yn = y.toDoubleOrNull()
            if (xn != null && yn != null) xn.compareTo(yn) else x.compareTo(y)
        }
        return if (direction == SortDirection.DESCENDING) result.reversed() else result
    }

    val queue: QueueApi = object : QueueApi {
        override suspend fun delete(
            id: Int,
            removeFromClient: Boolean?,
            blocklist: Boolean?,
            skipRedownload: Boolean?,
            changeCategory: Boolean?
        ): Unit {
            before("Queue.delete")
            queueStore.remove(id)
            return Unit
        }

        override suspend fun deleteBulk(
            removeFromClient: Boolean?,
            blocklist: Boolean?,
            skipRedownload: Boolean?,
            changeCategory: Boolean?,
            body: QueueBulkResource
        ): Unit {
            before("Queue.deleteBulk")
            body.ids.orEmpty().forEach(queueStore::remove)
            return Unit
        }

        override suspend fun list(
            page: Int?,
            pageSize: Int?,
            sortKey: String?,
            sortDirection: SortDirection?,
            includeUnknownSeriesItems: Boolean?,
            includeSeries: Boolean?,
            includeEpisode: Boolean?,
            seriesIds: List<Int>?,
            protocol: DownloadProtocol?,
            languages: List<Int>?,
            quality: List<Int>?,
            status: List<QueueStatus>?
        ): QueueResourcePagingResource {
            before("Queue.list")
            val rows = queueStore.values.filter {
                (seriesIds == null || it.seriesId in seriesIds) &&
                    (protocol == null || it.protocol == protocol) &&
                    (status == null || it.status in status) &&
                    (languages == null || it.languages.orEmpty().any { language ->
                        language.id in languages
                    }) &&
                    (quality == null || it.quality.quality.id in quality) &&
                    (includeUnknownSeriesItems == true || it.seriesId != null)
            }
            val sorted = sorted(rows, sortKey, sortDirection)
            return QueueResourcePagingResource(
                page = page ?: 1, pageSize = pageSize ?: 10, sortKey = sortKey,
                sortDirection = sortDirection ?: SortDirection.DEFAULT,
                totalRecords = rows.size, records = paged(sorted, page, pageSize)
            )
        }
    }

    val queueAction: QueueActionApi = object : QueueActionApi {
        override suspend fun create(
            id: Int
        ): Unit {
            before("QueueAction.create")
            val row = queueStore[id] ?: missing(id)
            queueStore[id] = row.copy(status = QueueStatus.DOWNLOADING)
            grabbedQueueIds.add(id)
            return Unit
        }

        override suspend fun createBulk(
            body: QueueBulkResource
        ): Unit {
            before("QueueAction.createBulk")
            body.ids.orEmpty().forEach { id ->
                val row = queueStore[id] ?: missing(id)
                queueStore[id] = row.copy(status = QueueStatus.DOWNLOADING)
                grabbedQueueIds.add(id)
            }
            return Unit
        }
    }

    val queueDetails: QueueDetailsApi = object : QueueDetailsApi {
        override suspend fun list(
            seriesId: Int?,
            episodeIds: List<Int>?,
            includeSeries: Boolean?,
            includeEpisode: Boolean?
        ): List<QueueResource> {
            before("QueueDetails.list")
            return queueStore.values.filter {
                (seriesId == null || it.seriesId == seriesId) &&
                    (episodeIds == null || it.episodeId in episodeIds)
            }
        }
    }

    val queueStatus: QueueStatusApi = object : QueueStatusApi {
        override suspend fun list(): QueueStatusResource {
            before("QueueStatus.list")
            val unknown = queueStore.values.filter { it.seriesId == null }
            return QueueStatusResource(
                totalCount = queueStore.size, count = queueStore.size - unknown.size,
                unknownCount = unknown.size,
                errors = queueStore.values.any {
                    it.trackedDownloadStatus == TrackedDownloadStatus.ERROR
                },
                warnings = queueStore.values.any {
                    it.trackedDownloadStatus == TrackedDownloadStatus.WARNING
                },
                unknownErrors = unknown.any {
                    it.trackedDownloadStatus == TrackedDownloadStatus.ERROR
                },
                unknownWarnings = unknown.any {
                    it.trackedDownloadStatus == TrackedDownloadStatus.WARNING
                }
            )
        }
    }

    val blocklist: BlocklistApi = object : BlocklistApi {
        override suspend fun list(
            page: Int?,
            pageSize: Int?,
            sortKey: String?,
            sortDirection: SortDirection?,
            seriesIds: List<Int>?,
            protocols: List<DownloadProtocol>?
        ): BlocklistResourcePagingResource {
            before("Blocklist.list")
            val rows = blocklistStore.values.filter {
                (seriesIds == null || it.seriesId in seriesIds) &&
                    (protocols == null || it.protocol in protocols)
            }
            return BlocklistResourcePagingResource(
                page = page ?: 1, pageSize = pageSize ?: 10, sortKey = sortKey,
                sortDirection = sortDirection ?: SortDirection.DEFAULT,
                totalRecords = rows.size,
                records = paged(sorted(rows, sortKey, sortDirection), page, pageSize)
            )
        }

        override suspend fun delete(
            id: Int
        ): Unit {
            before("Blocklist.delete")
            blocklistStore.remove(id)
            return Unit
        }

        override suspend fun deleteBulk(
            body: BlocklistBulkResource
        ): Unit {
            before("Blocklist.deleteBulk")
            body.ids.orEmpty().forEach(blocklistStore::remove)
            return Unit
        }
    }

    val history: HistoryApi = object : HistoryApi {
        override suspend fun list(
            page: Int?,
            pageSize: Int?,
            sortKey: String?,
            sortDirection: SortDirection?,
            includeSeries: Boolean?,
            includeEpisode: Boolean?,
            eventType: List<Int>?,
            episodeId: Int?,
            downloadId: String?,
            seriesIds: List<Int>?,
            languages: List<Int>?,
            quality: List<Int>?
        ): HistoryResourcePagingResource {
            before("History.list")
            val rows = historyStore.values.filter {
                (episodeId == null || it.episodeId == episodeId) &&
                    (downloadId == null || it.downloadId == downloadId) &&
                    (seriesIds == null || it.seriesId in seriesIds) &&
                    (eventType == null || it.eventType.ordinal in eventType) &&
                    (languages == null || it.languages.orEmpty().any { language ->
                        language.id in languages
                    }) &&
                    (quality == null || it.quality.quality.id in quality)
            }
            return HistoryResourcePagingResource(
                page = page ?: 1, pageSize = pageSize ?: 10, sortKey = sortKey,
                sortDirection = sortDirection ?: SortDirection.DEFAULT,
                totalRecords = rows.size,
                records = paged(sorted(rows, sortKey, sortDirection), page, pageSize)
            )
        }

        override suspend fun since(
            date: String?,
            eventType: EpisodeHistoryEventType?,
            includeSeries: Boolean?,
            includeEpisode: Boolean?
        ): List<HistoryResource> {
            before("History.since")
            return historyStore.values.filter {
                (date == null || Instant.parse(it.date) >= Instant.parse(date)) &&
                    (eventType == null || it.eventType == eventType)
            }
        }

        override suspend fun series(
            seriesId: Int?,
            seasonNumber: Int?,
            eventType: EpisodeHistoryEventType?,
            includeSeries: Boolean?,
            includeEpisode: Boolean?
        ): List<HistoryResource> {
            before("History.series")
            return historyStore.values.filter {
                (seriesId == null || it.seriesId == seriesId) &&
                    (eventType == null || it.eventType == eventType) &&
                    (seasonNumber == null ||
                        it.episode["seasonNumber"]?.toString() == seasonNumber.toString())
            }
        }

        override suspend fun markFailed(
            id: Int
        ): Unit {
            before("History.markFailed")
            val row = historyStore[id] ?: missing(id)
            historyStore[id] = row.copy(eventType = EpisodeHistoryEventType.DOWNLOAD_FAILED)
            return Unit
        }
    }

    val manualImport: ManualImportApi = object : ManualImportApi {
        override suspend fun list(
            folder: String?,
            downloadId: String?,
            seriesId: Int?,
            seasonNumber: Int?,
            filterExistingFiles: Boolean?
        ): List<ManualImportResource> {
            before("ManualImport.list")
            return manualImports.filter {
                (downloadId == null || it.downloadId == downloadId) &&
                    (folder == null || it.path?.startsWith(folder) == true) &&
                    (seriesId == null || it.series?.get("id")?.toString() == seriesId.toString()) &&
                    (seasonNumber == null || it.seasonNumber == seasonNumber) &&
                    (filterExistingFiles == false ||
                        it.episodeFileId == null || it.episodeFileId == 0)
            }
        }

        override suspend fun create(
            body: List<ManualImportReprocessResource>
        ): Unit {
            before("ManualImport.create")
            reprocessedImports.addAll(body)
            body.forEach { update ->
                val index = manualImports.indexOfFirst { it.id == update.id }
                if (index >= 0) {
                    manualImports[index] = manualImports[index].copy(
                        path = update.path, seasonNumber = update.seasonNumber,
                        episodes = update.episodes, quality = update.quality,
                        languages = update.languages, releaseGroup = update.releaseGroup,
                        customFormats = update.customFormats, indexerFlags = update.indexerFlags,
                        releaseType = update.releaseType, rejections = update.rejections
                    )
                }
            }
            return Unit
        }
    }

    val command: CommandApi = object : CommandApi {
        override suspend fun create(
            body: CommandResource
        ): CommandResource {
            before("Command.create")
            val id = body.id.takeIf { it > 0 } ?: ((commandStore.keys.maxOrNull() ?: 0) + 1)
            return body.copy(id = id).also { commandStore[id] = it }
        }

        override suspend fun list(): List<CommandResource> {
            before("Command.list")
            return commandStore.values.toList()
        }

        override suspend fun delete(
            id: Int
        ): Unit {
            before("Command.delete")
            commandStore.remove(id)
            return Unit
        }

        override suspend fun get(
            id: Int
        ): Response<CommandResource> {
            before("Command.get")
            return commandStore[id]?.let { Response.success(it) }
                ?: Response.error(404, "Missing command".toResponseBody())
        }
    }

    val release: ReleaseApi = object : ReleaseApi {
        override suspend fun create(
            body: ReleaseResource
        ): Unit {
            before("Release.create")
            grabbedReleases.add(body)
            return Unit
        }

        override suspend fun list(
            seriesId: Int?,
            episodeId: Int?,
            seasonNumber: Int?
        ): List<ReleaseResource> {
            before("Release.list")
            return releases.filter {
                (seriesId == null || it.seriesId == seriesId) &&
                    (episodeId == null || it.episodeId == episodeId ||
                        episodeId in it.episodeIds.orEmpty()) &&
                    (seasonNumber == null || it.seasonNumber == seasonNumber)
            }
        }
    }

    val releasePush: ReleasePushApi = object : ReleasePushApi {
        override suspend fun create(
            body: ReleaseResource
        ): List<ReleaseResource> {
            before("ReleasePush.create")
            releases.add(body)
            pushedReleases.add(body)
            return listOf(body)
        }
    }

    val parse: ParseApi = object : ParseApi {
        override suspend fun list(
            title: String?,
            path: String?
        ): ParseResource {
            before("Parse.list")
            return parsedTitles[title ?: path] ?: ParseResource(title = title ?: path)
        }
    }

    val ping: PingApi = object : PingApi {
        override suspend fun list(): Response<PingResource> {
            before("Ping.list")
            return if (pingStatusCode in 200..299) Response.success(pingResource)
            else Response.error(
                pingStatusCode, ArrJson.encodeToString(pingResource).toResponseBody()
            )
        }

        override suspend fun head(): Response<Unit> {
            before("Ping.head")
            return if (pingStatusCode in 200..299) Response.success(Unit)
            else Response.error(pingStatusCode, "".toResponseBody())
        }
    }
}
