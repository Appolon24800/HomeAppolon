package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.CommandResource
import dev.appolon.homelab.arr.api.prowlarr.model.CustomFilterResource
import dev.appolon.homelab.arr.api.prowlarr.model.HistoryEventType
import dev.appolon.homelab.arr.api.prowlarr.model.HistoryResource
import dev.appolon.homelab.arr.api.prowlarr.model.HistoryResourcePagingResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerStatsResource
import dev.appolon.homelab.arr.api.prowlarr.model.ReleaseResource
import dev.appolon.homelab.arr.api.prowlarr.model.SortDirection
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import kotlin.comparisons.nullsLast

/** One recorded fake invocation with its wire-shaped arguments. */
data class ProwlarrSearchCall(val operation: String, val arguments: Map<String, Any?>)

/**
 * In-memory implementation of every search-group interface for UI tests.
 * Seed the state, then drive screens through any interface.
 *
 * [latency] is suspending so tests inject delay without blocking; [failure]
 * throws before any state changes (transport or cancellation failures); and
 * [errorStatus] turns one call into an HTTP 4xx or 5xx, thrown as
 * [HttpException] for plain calls and returned inside [Response] for calls
 * whose signatures carry the status. [calls] records every invocation for
 * assertion. Reads of unknown ids throw the matching 404, mirroring the
 * server; updates upsert, which keeps edit flows testable without seeding
 * every row.
 *
 * History filtering follows the spec's shapes: [ProwlarrSearchCall] event
 * filters on the paged endpoint are ints matching the HistoryEventType
 * declaration order, and the `since` window compares ISO-8601 strings
 * lexicographically, which orders correctly for one consistent date format.
 * Newznab calls return fresh [ResponseBody]s seeded from [newznabBody] and
 * [downloadBody] so each consume-and-close behaves like the network.
 * Configure and use the fake from one test dispatcher.
 */
class FakeProwlarrSearch(
    var latency: suspend (ProwlarrSearchCall) -> Unit = {},
    var errorStatus: (ProwlarrSearchCall) -> Int? = { null },
    var failure: (ProwlarrSearchCall) -> Throwable? = { null },
) : SearchApi,
    NewznabApi,
    HistoryApi,
    IndexerStatsApi,
    CustomFilterApi,
    CommandApi {

    val calls: MutableList<ProwlarrSearchCall> = mutableListOf()
    val releases = mutableListOf<ReleaseResource>()
    val grabs = mutableListOf<ReleaseResource>()
    val history = mutableListOf<HistoryResource>()
    val customFilters = mutableMapOf<Int, CustomFilterResource>()
    val commands = mutableMapOf<Int, CommandResource>()

    var newznabBody: String =
        """<?xml version="1.0" encoding="UTF-8"?><rss version="2.0"></rss>"""
    var downloadBody: ByteArray = byteArrayOf()
    var indexerStats: IndexerStatsResource = IndexerStatsResource()

    /** Ids handed to creates whose resource carries no id. */
    var nextId = 100

    private suspend fun gate(operation: String, vararg arguments: Pair<String, Any?>): Int? {
        val call = ProwlarrSearchCall(operation, arguments.toMap())
        calls += call
        latency(call)
        failure(call)?.let { throw it }
        val status = errorStatus(call)
        require(status == null || status in 400..599) { "Injected status must be 4xx or 5xx" }
        return status
    }

    /** Gate for plain-returning calls: injected statuses throw, like Retrofit. */
    private suspend fun read(operation: String, vararg arguments: Pair<String, Any?>) {
        gate(operation, *arguments)?.let(::httpError)
    }

    private fun httpError(status: Int): Nothing =
        throw HttpException(Response.error<Unit>(status, "{}".toResponseBody()))

    private fun pathId(id: String, fallback: Int): Int = id.toIntOrNull() ?: fallback

    private fun <T : Any> stored(map: Map<Int, T>, id: Int): T = map[id] ?: httpError(404)

    private fun freeId(resourceId: Int): Int = resourceId.takeIf { it != 0 } ?: ++nextId

    override suspend fun getSearch(
        query: String?,
        type: String?,
        indexerIds: List<Int>?,
        categories: List<Int>?,
        limit: Int?,
        offset: Int?,
    ): List<ReleaseResource> {
        read(
            "getSearch",
            "query" to query,
            "type" to type,
            "indexerIds" to indexerIds,
            "categories" to categories,
            "limit" to limit,
            "offset" to offset,
        )
        var matched = releases.toList()
        query?.let { text ->
            matched = matched.filter { it.title?.contains(text, ignoreCase = true) == true }
        }
        indexerIds?.let { ids -> matched = matched.filter { it.indexerId in ids } }
        categories?.let { wanted ->
            matched = matched.filter { release ->
                release.categories.orEmpty().any { it.id in wanted }
            }
        }
        val from = (offset ?: 0).coerceAtLeast(0)
        val until = limit?.let { from + it } ?: Int.MAX_VALUE
        return matched.drop(from).take((until - from).coerceAtLeast(0))
    }

    override suspend fun createSearch(resource: ReleaseResource): ReleaseResource {
        read("createSearch", "resource" to resource)
        grabs += resource
        return resource
    }

    override suspend fun createSearchBulk(resources: List<ReleaseResource>): ReleaseResource {
        read("createSearchBulk", "resources" to resources)
        grabs += resources
        return resources.firstOrNull() ?: ReleaseResource()
    }

    private fun fresh(body: String, mediaType: String): ResponseBody =
        body.toByteArray().toResponseBody(mediaType.toMediaType())

    private companion object {
        private val OCTET_STREAM = "application/octet-stream".toMediaType()
    }

    override suspend fun getNewznab(
        id: Int,
        t: String?,
        q: String?,
        cat: String?,
        imdbid: String?,
        tmdbid: Int?,
        extended: String?,
        limit: Int?,
        offset: Int?,
        minage: Int?,
        maxage: Int?,
        minsize: Long?,
        maxsize: Long?,
        rid: Int?,
        tvmazeid: Int?,
        traktid: Int?,
        tvdbid: Int?,
        doubanid: Int?,
        season: Int?,
        ep: String?,
        album: String?,
        artist: String?,
        label: String?,
        track: String?,
        year: Int?,
        genre: String?,
        author: String?,
        title: String?,
        publisher: String?,
        configured: String?,
        source: String?,
        host: String?,
        server: String?,
    ): ResponseBody {
        read(
            "getNewznab",
            "id" to id,
            "t" to t,
            "q" to q,
            "cat" to cat,
            "imdbid" to imdbid,
            "tmdbid" to tmdbid,
            "extended" to extended,
            "limit" to limit,
            "offset" to offset,
            "minage" to minage,
            "maxage" to maxage,
            "minsize" to minsize,
            "maxsize" to maxsize,
            "rid" to rid,
            "tvmazeid" to tvmazeid,
            "traktid" to traktid,
            "tvdbid" to tvdbid,
            "doubanid" to doubanid,
            "season" to season,
            "ep" to ep,
            "album" to album,
            "artist" to artist,
            "label" to label,
            "track" to track,
            "year" to year,
            "genre" to genre,
            "author" to author,
            "title" to title,
            "publisher" to publisher,
            "configured" to configured,
            "source" to source,
            "host" to host,
            "server" to server,
        )
        return fresh(newznabBody, "application/xml")
    }

    override suspend fun getNewznabLegacy(
        id: Int,
        t: String?,
        q: String?,
        cat: String?,
        imdbid: String?,
        tmdbid: Int?,
        extended: String?,
        limit: Int?,
        offset: Int?,
        minage: Int?,
        maxage: Int?,
        minsize: Long?,
        maxsize: Long?,
        rid: Int?,
        tvmazeid: Int?,
        traktid: Int?,
        tvdbid: Int?,
        doubanid: Int?,
        season: Int?,
        ep: String?,
        album: String?,
        artist: String?,
        label: String?,
        track: String?,
        year: Int?,
        genre: String?,
        author: String?,
        title: String?,
        publisher: String?,
        configured: String?,
        source: String?,
        host: String?,
        server: String?,
    ): ResponseBody {
        read(
            "getNewznabLegacy",
            "id" to id,
            "t" to t,
            "q" to q,
            "cat" to cat,
            "imdbid" to imdbid,
            "tmdbid" to tmdbid,
            "extended" to extended,
            "limit" to limit,
            "offset" to offset,
            "minage" to minage,
            "maxage" to maxage,
            "minsize" to minsize,
            "maxsize" to maxsize,
            "rid" to rid,
            "tvmazeid" to tvmazeid,
            "traktid" to traktid,
            "tvdbid" to tvdbid,
            "doubanid" to doubanid,
            "season" to season,
            "ep" to ep,
            "album" to album,
            "artist" to artist,
            "label" to label,
            "track" to track,
            "year" to year,
            "genre" to genre,
            "author" to author,
            "title" to title,
            "publisher" to publisher,
            "configured" to configured,
            "source" to source,
            "host" to host,
            "server" to server,
        )
        return fresh(newznabBody, "application/xml")
    }

    override suspend fun getDownload(id: Int, link: String?, file: String?): ResponseBody {
        read("getDownload", "id" to id, "link" to link, "file" to file)
        return downloadBody.toResponseBody(OCTET_STREAM)
    }

    override suspend fun getDownloadLegacy(id: Int, link: String?, file: String?): ResponseBody {
        read("getDownloadLegacy", "id" to id, "link" to link, "file" to file)
        return downloadBody.toResponseBody(OCTET_STREAM)
    }

    override suspend fun getHistory(
        page: Int?,
        pageSize: Int?,
        sortKey: String?,
        sortDirection: SortDirection?,
        eventType: List<Int>?,
        successful: Boolean?,
        downloadId: String?,
        indexerIds: List<Int>?,
    ): HistoryResourcePagingResource {
        read(
            "getHistory",
            "page" to page,
            "pageSize" to pageSize,
            "sortKey" to sortKey,
            "sortDirection" to sortDirection,
            "eventType" to eventType,
            "successful" to successful,
            "downloadId" to downloadId,
            "indexerIds" to indexerIds,
        )
        var matched = history.toList()
        eventType?.let { types ->
            matched = matched.filter { it.eventType.ordinal in types }
        }
        successful?.let { flag -> matched = matched.filter { it.successful == flag } }
        downloadId?.let { id -> matched = matched.filter { it.downloadId == id } }
        indexerIds?.let { ids -> matched = matched.filter { it.indexerId in ids } }
        if (sortKey == "date") {
            val byDate = matched.sortedWith(compareBy(nullsLast()) { it.date })
            matched = if (sortDirection == SortDirection.DESCENDING) byDate.asReversed() else byDate
        }
        val effectivePage = page ?: 1
        val effectiveSize = pageSize ?: 10
        val from = (effectivePage - 1) * effectiveSize
        val window = matched.drop(from).take(effectiveSize)
        return HistoryResourcePagingResource(
            page = effectivePage,
            pageSize = effectiveSize,
            sortKey = sortKey,
            sortDirection = sortDirection ?: SortDirection.DEFAULT,
            totalRecords = matched.size,
            records = window,
        )
    }

    override suspend fun getHistorySince(
        date: String?,
        eventType: HistoryEventType?,
    ): List<HistoryResource> {
        read("getHistorySince", "date" to date, "eventType" to eventType)
        return history.filter { record ->
            (date == null || (record.date ?: "") >= date) &&
                (eventType == null || record.eventType == eventType)
        }
    }

    override suspend fun getHistoryForIndexer(
        indexerId: Int?,
        eventType: HistoryEventType?,
        limit: Int?,
    ): List<HistoryResource> {
        read(
            "getHistoryForIndexer",
            "indexerId" to indexerId,
            "eventType" to eventType,
            "limit" to limit,
        )
        val matched = history.filter { record ->
            (indexerId == null || record.indexerId == indexerId) &&
                (eventType == null || record.eventType == eventType)
        }
        return limit?.let(matched::take) ?: matched
    }

    override suspend fun getIndexerStats(
        startDate: String?,
        endDate: String?,
        indexers: String?,
        protocols: String?,
        tags: String?,
    ): IndexerStatsResource {
        read(
            "getIndexerStats",
            "startDate" to startDate,
            "endDate" to endDate,
            "indexers" to indexers,
            "protocols" to protocols,
            "tags" to tags,
        )
        return indexerStats
    }

    override suspend fun getCustomFilters(): List<CustomFilterResource> {
        read("getCustomFilters")
        return customFilters.values.toList()
    }

    override suspend fun getCustomFilter(id: Int): CustomFilterResource {
        read("getCustomFilter", "id" to id)
        return stored(customFilters, id)
    }

    override suspend fun createCustomFilter(resource: CustomFilterResource):
        CustomFilterResource {
        read("createCustomFilter", "resource" to resource)
        val stored = resource.copy(id = freeId(resource.id))
        customFilters[stored.id] = stored
        return stored
    }

    override suspend fun updateCustomFilter(
        id: String,
        resource: CustomFilterResource,
    ): CustomFilterResource {
        read("updateCustomFilter", "id" to id, "resource" to resource)
        val key = pathId(id, resource.id)
        val stored = resource.copy(id = key)
        customFilters[key] = stored
        return stored
    }

    override suspend fun deleteCustomFilter(id: Int): Response<Unit> {
        val status = gate("deleteCustomFilter", "id" to id)
        customFilters.remove(id)
        return status?.let { Response.error(it, "{}".toResponseBody()) }
            ?: Response.success(200, Unit)
    }

    override suspend fun getCommands(): List<CommandResource> {
        read("getCommands")
        return commands.values.toList()
    }

    override suspend fun getCommand(id: Int): CommandResource {
        read("getCommand", "id" to id)
        return stored(commands, id)
    }

    override suspend fun createCommand(resource: CommandResource): CommandResource {
        read("createCommand", "resource" to resource)
        val stored = resource.copy(id = freeId(resource.id))
        commands[stored.id] = stored
        return stored
    }

    override suspend fun deleteCommand(id: Int): Response<Unit> {
        val status = gate("deleteCommand", "id" to id)
        commands.remove(id)
        return status?.let { Response.error(it, "{}".toResponseBody()) }
            ?: Response.success(200, Unit)
    }
}
