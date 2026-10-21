package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.AlbumResourcePagingResource
import dev.appolon.homelab.arr.api.lidarr.model.ApplyTags
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.CustomFilterResource
import dev.appolon.homelab.arr.api.lidarr.model.ImportListBulkResource
import dev.appolon.homelab.arr.api.lidarr.model.ImportListExclusionResource
import dev.appolon.homelab.arr.api.lidarr.model.ImportListResource
import dev.appolon.homelab.arr.api.lidarr.model.SearchResource
import dev.appolon.homelab.arr.api.lidarr.model.SortDirection
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/**
 * In-memory implementations of every discovery client. Seed collections before
 * UI tests. [latency] can suspend on the test scheduler and [error] runs before
 * each operation, keyed by operation name.
 *
 * Static resources resolve against [staticResources] by path; missing paths and
 * missing entity ids surface as HttpException 404 like the real server.
 */
class FakeLidarrDiscovery(
    var latency: suspend (operation: String) -> Unit = {},
    var error: (operation: String) -> Throwable? = { null },
) : ImportListApi, ImportListExclusionApi, CustomFilterApi, MissingApi, CutoffApi,
    SearchApi, StaticResourceApi {
    val importLists: MutableMap<Int, ImportListResource> = linkedMapOf()
    val importListExclusions: MutableMap<Int, ImportListExclusionResource> = linkedMapOf()
    val customFilters: MutableMap<Int, CustomFilterResource> = linkedMapOf()
    /** Album pool for the missing queue, keyed by album id. */
    val missing: MutableMap<Int, AlbumResource> = linkedMapOf()
    /** Album pool for the cutoff queue, keyed by album id. */
    val cutoff: MutableMap<Int, AlbumResource> = linkedMapOf()
    /** Artists joined into wanted albums when includeArtist is true. */
    val artists: MutableMap<Int, ArtistResource> = linkedMapOf()
    val searchResults: MutableList<SearchResource> = mutableListOf()
    /** Static assets by path, served for content/{path} and {path} requests. */
    val staticResources: MutableMap<String, String> = linkedMapOf()
    val testedImportLists: MutableList<ImportListResource> = mutableListOf()
    val importListActions: MutableMap<String, MutableList<ImportListResource>> = linkedMapOf()
    var testAllCount: Int = 0
        private set
    var loginPageHtml: String = "<html>login</html>"
    var rootHtml: String = "<html>lidarr</html>"

    private suspend fun before(operation: String) {
        latency(operation)
        error(operation)?.let { throw it }
    }

    private fun missingId(): Nothing = throw HttpException(
        Response.error<Unit>(404, "{}".toResponseBody("application/json".toMediaType()))
    )

    private fun nextId(ids: Set<Int>): Int = (ids.maxOrNull() ?: 0) + 1

    private fun <T> page(items: List<T>, page: Int?, size: Int?): List<T> {
        val start = ((page ?: 1).coerceAtLeast(1).toLong() - 1) * (size ?: 10).coerceAtLeast(1)
        return items.drop(start.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            .take((size ?: 10).coerceAtLeast(1))
    }

    private suspend fun wantedPage(
        operation: String,
        pool: Map<Int, AlbumResource>,
        page: Int?,
        pageSize: Int?,
        sortKey: String?,
        sortDirection: SortDirection?,
        includeArtist: Boolean?,
        monitored: Boolean?,
    ): AlbumResourcePagingResource {
        before(operation)
        val filtered = pool.values.filter { it.monitored == (monitored ?: true) }
        val ordered = filtered.sortedWith { a, b ->
            compareValues(
                when (sortKey) {
                    "title" -> a.title
                    "releaseDate" -> a.releaseDate
                    "albumType" -> a.albumType
                    else -> a.id.toString()
                },
                when (sortKey) {
                    "title" -> b.title
                    "releaseDate" -> b.releaseDate
                    "albumType" -> b.albumType
                    else -> b.id.toString()
                },
            )
        }
        val matches = if (sortDirection == SortDirection.DESCENDING) ordered.reversed() else ordered
        return AlbumResourcePagingResource(
            page = page ?: 1,
            pageSize = pageSize ?: 10,
            sortKey = sortKey,
            sortDirection = sortDirection ?: SortDirection.DEFAULT,
            totalRecords = matches.size,
            records = page(matches, page, pageSize).map {
                it.copy(artist = if (includeArtist == true) artists[it.artistId] else null)
            },
        )
    }

    private fun staticResponse(content: String, mediaType: String): Response<ResponseBody> =
        Response.success(content.toResponseBody(mediaType.toMediaType()))

    private fun notFoundResponse(): Response<ResponseBody> = Response.error(
        404, "".toResponseBody("text/plain".toMediaType())
    )

    override suspend fun getImportLists(): List<ImportListResource> {
        before("getImportLists")
        return importLists.values.toList()
    }

    override suspend fun getImportList(id: Int): ImportListResource {
        before("getImportList")
        return importLists[id] ?: missingId()
    }

    override suspend fun getImportListSchema(): List<ImportListResource> {
        before("getImportListSchema")
        // Schemas are provider templates, not stored state; presets stand in for them.
        return importLists.values.filter { it.presets != null }
    }

    override suspend fun addImportList(
        forceSave: Boolean?,
        resource: ImportListResource,
    ): ImportListResource {
        before("addImportList")
        val id = nextId(importLists.keys)
        val created = resource.copy(id = id)
        importLists[id] = created
        return created
    }

    override suspend fun updateImportList(
        id: Int,
        forceSave: Boolean?,
        resource: ImportListResource,
    ): ImportListResource {
        before("updateImportList")
        if (id !in importLists) missingId()
        val updated = resource.copy(id = id)
        importLists[id] = updated
        return updated
    }

    override suspend fun updateImportLists(resource: ImportListBulkResource): ImportListResource {
        before("updateImportLists")
        val targets = resource.ids.orEmpty().mapNotNull { importLists[it] }
        if (targets.isEmpty()) missingId()
        var updated: ImportListResource? = null
        for (target in targets) {
            val merged = target.copy(
                tags = when (resource.applyTags) {
                    ApplyTags.ADD -> target.tags.orEmpty() + resource.tags.orEmpty()
                    ApplyTags.REMOVE -> target.tags.orEmpty() - resource.tags.orEmpty()
                    else -> (resource.tags ?: target.tags).orEmpty()
                }.toSortedSet(),
                enableAutomaticAdd = resource.enableAutomaticAdd ?: target.enableAutomaticAdd,
                rootFolderPath = resource.rootFolderPath ?: target.rootFolderPath,
                qualityProfileId = resource.qualityProfileId ?: target.qualityProfileId,
            )
            importLists[merged.id] = merged
            updated = merged
        }
        // The spec types the bulk response as one resource; return the last merged item.
        return updated ?: missingId()
    }

    override suspend fun deleteImportLists(resource: ImportListBulkResource) {
        before("deleteImportLists")
        resource.ids.orEmpty().forEach { importLists.remove(it) }
    }

    override suspend fun deleteImportList(id: Int) {
        before("deleteImportList")
        importLists.remove(id) ?: missingId()
    }

    override suspend fun testImportList(forceTest: Boolean?, resource: ImportListResource) {
        before("testImportList")
        testedImportLists += resource
    }

    override suspend fun testAllImportLists() {
        before("testAllImportLists")
        testAllCount++
    }

    override suspend fun importListAction(name: String, resource: ImportListResource) {
        before("importListAction")
        importListActions.getOrPut(name) { mutableListOf() } += resource
    }

    override suspend fun getImportListExclusions(): List<ImportListExclusionResource> {
        before("getImportListExclusions")
        return importListExclusions.values.toList()
    }

    override suspend fun getImportListExclusion(id: Int): ImportListExclusionResource {
        before("getImportListExclusion")
        return importListExclusions[id] ?: missingId()
    }

    override suspend fun addImportListExclusion(
        resource: ImportListExclusionResource,
    ): ImportListExclusionResource {
        before("addImportListExclusion")
        val id = nextId(importListExclusions.keys)
        val created = resource.copy(id = id)
        importListExclusions[id] = created
        return created
    }

    override suspend fun updateImportListExclusion(
        id: String,
        resource: ImportListExclusionResource,
    ): ImportListExclusionResource {
        before("updateImportListExclusion")
        val intId = id.toIntOrNull() ?: missingId()
        if (intId !in importListExclusions) missingId()
        val updated = resource.copy(id = intId)
        importListExclusions[intId] = updated
        return updated
    }

    override suspend fun deleteImportListExclusion(id: Int) {
        before("deleteImportListExclusion")
        importListExclusions.remove(id) ?: missingId()
    }

    override suspend fun getCustomFilters(): List<CustomFilterResource> {
        before("getCustomFilters")
        return customFilters.values.toList()
    }

    override suspend fun getCustomFilter(id: Int): CustomFilterResource {
        before("getCustomFilter")
        return customFilters[id] ?: missingId()
    }

    override suspend fun addCustomFilter(resource: CustomFilterResource): CustomFilterResource {
        before("addCustomFilter")
        val id = nextId(customFilters.keys)
        val created = resource.copy(id = id)
        customFilters[id] = created
        return created
    }

    override suspend fun updateCustomFilter(
        id: String,
        resource: CustomFilterResource,
    ): CustomFilterResource {
        before("updateCustomFilter")
        val intId = id.toIntOrNull() ?: missingId()
        if (intId !in customFilters) missingId()
        val updated = resource.copy(id = intId)
        customFilters[intId] = updated
        return updated
    }

    override suspend fun deleteCustomFilter(id: Int) {
        before("deleteCustomFilter")
        customFilters.remove(id) ?: missingId()
    }

    override suspend fun getMissing(
        page: Int?,
        pageSize: Int?,
        sortKey: String?,
        sortDirection: SortDirection?,
        includeArtist: Boolean?,
        monitored: Boolean?,
    ): AlbumResourcePagingResource = wantedPage(
        "getMissing", missing, page, pageSize, sortKey, sortDirection, includeArtist, monitored
    )

    override suspend fun getMissingById(id: Int): AlbumResource {
        before("getMissingById")
        return missing[id]?.copy(artist = artists[missing[id]?.artistId]) ?: missingId()
    }

    override suspend fun getCutoff(
        page: Int?,
        pageSize: Int?,
        sortKey: String?,
        sortDirection: SortDirection?,
        includeArtist: Boolean?,
        monitored: Boolean?,
    ): AlbumResourcePagingResource = wantedPage(
        "getCutoff", cutoff, page, pageSize, sortKey, sortDirection, includeArtist, monitored
    )

    override suspend fun getCutoffById(id: Int): AlbumResource {
        before("getCutoffById")
        return cutoff[id]?.copy(artist = artists[cutoff[id]?.artistId]) ?: missingId()
    }

    override suspend fun search(term: String?): List<SearchResource> {
        before("search")
        val query = term?.trim()
        if (query.isNullOrEmpty()) return searchResults.toList()
        return searchResults.filter {
            it.artist?.artistName?.contains(query, ignoreCase = true) == true ||
                it.album?.title?.contains(query, ignoreCase = true) == true
        }
    }

    override suspend fun getLoginPage(): Response<ResponseBody> {
        before("getLoginPage")
        return staticResponse(loginPageHtml, "text/html")
    }

    override suspend fun getContent(path: String): Response<ResponseBody> {
        before("getContent")
        val content = staticResources[path] ?: return notFoundResponse()
        return staticResponse(content, "application/octet-stream")
    }

    override suspend fun getRoot(): Response<ResponseBody> {
        before("getRoot")
        return staticResponse(rootHtml, "text/html")
    }

    override suspend fun getStaticResource(path: String): Response<ResponseBody> {
        before("getStaticResource")
        val content = staticResources[path] ?: return notFoundResponse()
        return staticResponse(content, "application/octet-stream")
    }
}
