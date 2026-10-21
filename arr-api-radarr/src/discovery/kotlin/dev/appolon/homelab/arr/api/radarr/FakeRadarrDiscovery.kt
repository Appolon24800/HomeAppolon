package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.ApplyTags
import dev.appolon.homelab.arr.api.radarr.model.CollectionResource
import dev.appolon.homelab.arr.api.radarr.model.CollectionUpdateResource
import dev.appolon.homelab.arr.api.radarr.model.CustomFilterResource
import dev.appolon.homelab.arr.api.radarr.model.ImportListBulkResource
import dev.appolon.homelab.arr.api.radarr.model.ImportListConfigResource
import dev.appolon.homelab.arr.api.radarr.model.ImportListExclusionBulkResource
import dev.appolon.homelab.arr.api.radarr.model.ImportListExclusionResource
import dev.appolon.homelab.arr.api.radarr.model.ImportListExclusionResourcePagingResource
import dev.appolon.homelab.arr.api.radarr.model.ImportListMoviesResource
import dev.appolon.homelab.arr.api.radarr.model.ImportListResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResourcePagingResource
import dev.appolon.homelab.arr.api.radarr.model.ProviderTestAllResult
import dev.appolon.homelab.arr.api.radarr.model.SortDirection
import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.time.Instant

/**
 * Mutable single-test state implementing every discovery-group interface.
 * Inject a suspending [sleep] such as coroutine delay to use virtual time without a runtime
 * coroutine dependency. The default sleep blocks the calling thread, so UI tests should inject it.
 * This fake contacts no list providers and runs no searches; provider interactions such as
 * [ImportListApi.testImportList] and [ImportListApi.importListAction] are only recorded.
 * [errorFor] receives the operation name and runs before any state mutation.
 */
class FakeRadarrDiscovery(
    var latencyMs: Long = 0,
    var errorFor: (String) -> Throwable? = { null },
    private val sleep: suspend (Long) -> Unit = { Thread.sleep(it) },
    private val now: () -> String = { Instant.now().toString() },
) : ImportListApi, ImportListConfigApi, ImportListExclusionApi, ImportListMoviesApi,
    CollectionApi, CustomFilterApi, MissingApi, CutoffApi {

    val importLists = mutableListOf<ImportListResource>()
    val importListSchemas = mutableListOf<ImportListResource>()
    val exclusions = mutableListOf<ImportListExclusionResource>()
    val discoveredMovies = mutableListOf<ImportListMoviesResource>()
    val movies = mutableListOf<MovieResource>()
    val collections = mutableListOf<CollectionResource>()
    val customFilters = mutableListOf<CustomFilterResource>()
    var importListConfig = ImportListConfigResource()

    /** Provider interactions the fake cannot perform, kept for assertions. */
    val testedImportLists = mutableListOf<ImportListResource>()
    val listActions = mutableListOf<Pair<String, ImportListResource>>()
    val bulkMonitorMovies = mutableListOf<Boolean?>()

    /** Tracks the effective value of each collection field bulk-updated with null left unset. */
    data class BulkUpdate(
        val collectionIds: List<Int>,
        val monitored: Boolean?,
        val searchOnAdd: Boolean?,
        val qualityProfileId: Int?,
        val rootFolderPath: String?,
    )

    val collectionBulkUpdates = mutableListOf<BulkUpdate>()

    private suspend fun before(operation: String) {
        require(latencyMs >= 0) { "Latency must be nonnegative" }
        if (latencyMs > 0) sleep(latencyMs)
        errorFor(operation)?.let { throw it }
    }

    private fun <T> notFound(): Response<T> = Response.error(404, "{}".toResponseBody())

    private fun emptyObject(): JsonObject = JsonObject(emptyMap())

    private fun nextId(ids: Iterable<Int>): Int = (ids.maxOrNull() ?: 0) + 1

    private fun exclusionPage(
        items: List<ImportListExclusionResource>,
        page: Int?,
        pageSize: Int?,
        sortKey: String?,
        sortDirection: SortDirection?,
    ): ImportListExclusionResourcePagingResource {
        val size = pageSize ?: items.size.coerceAtLeast(1)
        val from = ((page ?: 1) - 1) * size
        val slice = if (from >= items.size) {
            emptyList()
        } else {
            items.subList(from, (from + size).coerceAtMost(items.size))
        }
        return ImportListExclusionResourcePagingResource(
            page = page ?: 1,
            pageSize = size,
            sortKey = sortKey,
            sortDirection = sortDirection ?: SortDirection.DEFAULT,
            totalRecords = items.size,
            records = slice,
        )
    }

    private fun wantedItems(monitored: Boolean?): List<MovieResource> =
        movies.filter { it.hasFile != true && (monitored == null || it.monitored == monitored) }

    override suspend fun collections(tmdbId: Int?): List<CollectionResource> {
        before("collections")
        return collections.toList().filter { tmdbId == null || it.tmdbId == tmdbId }
    }

    override suspend fun updateCollections(
        body: CollectionUpdateResource,
    ): List<CollectionResource> {
        before("updateCollections")
        bulkMonitorMovies += body.monitorMovies
        val ids = body.collectionIds.orEmpty()
        collectionBulkUpdates += BulkUpdate(
            ids, body.monitored, body.searchOnAdd, body.qualityProfileId, body.rootFolderPath,
        )
        collections.replaceAll { collection ->
            if (collection.id !in ids) {
                collection
            } else {
                collection.copy(
                    monitored = body.monitored ?: collection.monitored,
                    searchOnAdd = body.searchOnAdd ?: collection.searchOnAdd,
                    qualityProfileId = body.qualityProfileId ?: collection.qualityProfileId,
                    rootFolderPath = body.rootFolderPath ?: collection.rootFolderPath,
                    minimumAvailability = body.minimumAvailability
                        ?: collection.minimumAvailability,
                )
            }
        }
        return collections.toList()
    }

    override suspend fun updateCollection(
        id: String,
        body: CollectionResource,
    ): Response<CollectionResource> {
        before("updateCollection")
        val idInt = id.toIntOrNull() ?: return notFound()
        val index = collections.indexOfFirst { it.id == idInt }
        if (index < 0) return notFound()
        val updated = body.copy(id = idInt)
        collections[index] = updated
        return Response.success(202, updated)
    }

    override suspend fun collection(id: Int): Response<CollectionResource> {
        before("collection")
        val found = collections.firstOrNull { it.id == id } ?: return notFound()
        return Response.success(found)
    }

    override suspend fun customFilters(): List<CustomFilterResource> {
        before("customFilters")
        return customFilters.toList()
    }

    override suspend fun createCustomFilter(
        body: CustomFilterResource,
    ): Response<CustomFilterResource> {
        before("createCustomFilter")
        val created = body.copy(id = nextId(customFilters.map { it.id }))
        customFilters += created
        return Response.success(201, created)
    }

    override suspend fun updateCustomFilter(
        id: String,
        body: CustomFilterResource,
    ): Response<CustomFilterResource> {
        before("updateCustomFilter")
        val idInt = id.toIntOrNull() ?: return notFound()
        val index = customFilters.indexOfFirst { it.id == idInt }
        if (index < 0) return notFound()
        val updated = body.copy(id = idInt)
        customFilters[index] = updated
        return Response.success(202, updated)
    }

    override suspend fun removeCustomFilter(id: Int): Response<Unit> {
        before("removeCustomFilter")
        if (customFilters.removeAll { it.id == id }) return Response.success(Unit)
        return notFound()
    }

    override suspend fun customFilter(id: Int): Response<CustomFilterResource> {
        before("customFilter")
        val found = customFilters.firstOrNull { it.id == id } ?: return notFound()
        return Response.success(found)
    }

    override suspend fun cutoff(
        page: Int?,
        pageSize: Int?,
        sortKey: String?,
        sortDirection: SortDirection?,
        monitored: Boolean?,
    ): MovieResourcePagingResource {
        before("cutoff")
        val all = wantedItems(monitored)
        val size = pageSize ?: all.size.coerceAtLeast(1)
        val from = ((page ?: 1) - 1) * size
        val slice = if (from >= all.size) {
            emptyList()
        } else {
            all.subList(from, (from + size).coerceAtMost(all.size))
        }
        return MovieResourcePagingResource(
            page = page ?: 1,
            pageSize = size,
            sortKey = sortKey,
            sortDirection = sortDirection ?: SortDirection.DEFAULT,
            totalRecords = all.size,
            records = slice,
        )
    }

    override suspend fun missing(
        page: Int?,
        pageSize: Int?,
        sortKey: String?,
        sortDirection: SortDirection?,
        monitored: Boolean?,
    ): MovieResourcePagingResource = cutoff(page, pageSize, sortKey, sortDirection, monitored)

    override suspend fun importLists(): List<ImportListResource> {
        before("importLists")
        return importLists.toList()
    }

    override suspend fun createImportList(
        forceSave: Boolean?,
        body: ImportListResource,
    ): Response<ImportListResource> {
        before("createImportList")
        val created = if (body.id == 0) {
            body.copy(id = nextId(importLists.map { it.id }))
        } else {
            body
        }
        importLists += created
        return Response.success(201, created)
    }

    override suspend fun updateImportList(
        id: Int,
        forceSave: Boolean?,
        body: ImportListResource,
    ): Response<ImportListResource> {
        before("updateImportList")
        val index = importLists.indexOfFirst { it.id == id }
        if (index < 0) return notFound()
        val updated = body.copy(id = id)
        importLists[index] = updated
        return Response.success(202, updated)
    }

    override suspend fun removeImportList(id: Int): JsonObject {
        before("removeImportList")
        importLists.removeAll { it.id == id }
        return emptyObject()
    }

    override suspend fun importList(id: Int): Response<ImportListResource> {
        before("importList")
        val found = importLists.firstOrNull { it.id == id } ?: return notFound()
        return Response.success(found)
    }

    override suspend fun updateImportListBulk(
        body: ImportListBulkResource,
    ): Response<List<ImportListResource>> {
        before("updateImportListBulk")
        val ids = body.ids.orEmpty()
        importLists.replaceAll { list ->
            if (list.id !in ids) {
                list
            } else {
                list.copy(
                    enabled = body.enabled ?: list.enabled,
                    enableAuto = body.enableAuto ?: list.enableAuto,
                    rootFolderPath = body.rootFolderPath ?: list.rootFolderPath,
                    qualityProfileId = body.qualityProfileId ?: list.qualityProfileId,
                    minimumAvailability = body.minimumAvailability ?: list.minimumAvailability,
                    tags = mergeTags(list.tags, body.tags, body.applyTags),
                )
            }
        }
        return Response.success(importLists.toList())
    }

    override suspend fun removeImportListBulk(body: ImportListBulkResource): JsonObject {
        before("removeImportListBulk")
        importLists.removeAll { it.id in body.ids.orEmpty() }
        return emptyObject()
    }

    override suspend fun importListSchema(): List<ImportListResource> {
        before("importListSchema")
        return importListSchemas.toList()
    }

    override suspend fun testImportList(
        forceTest: Boolean?,
        body: ImportListResource,
    ): Response<Unit> {
        before("testImportList")
        testedImportLists += body
        return Response.success(204, Unit)
    }

    override suspend fun testAllImportLists(): Response<List<ProviderTestAllResult>> {
        before("testAllImportLists")
        return Response.success(
            importLists.map { ProviderTestAllResult(id = it.id, isValid = true) },
        )
    }

    override suspend fun importListAction(name: String, body: ImportListResource): Response<Unit> {
        before("importListAction")
        listActions += name to body
        return Response.success(Unit)
    }

    override suspend fun importListConfig(): ImportListConfigResource {
        before("importListConfig")
        return importListConfig
    }

    override suspend fun updateImportListConfig(
        id: String,
        body: ImportListConfigResource,
    ): Response<ImportListConfigResource> {
        before("updateImportListConfig")
        importListConfig = body.copy(id = id.toIntOrNull() ?: body.id)
        return Response.success(202, importListConfig)
    }

    override suspend fun importListConfigById(id: Int): Response<ImportListConfigResource> {
        before("importListConfigById")
        if (importListConfig.id != id) return notFound()
        return Response.success(importListConfig)
    }

    override suspend fun exclusions(): List<ImportListExclusionResource> {
        before("exclusions")
        return exclusions.toList()
    }

    override suspend fun createExclusion(
        body: ImportListExclusionResource,
    ): Response<ImportListExclusionResource> {
        before("createExclusion")
        val created = if (body.id == 0) {
            body.copy(id = nextId(exclusions.map { it.id }))
        } else {
            body
        }
        exclusions += created
        return Response.success(201, created)
    }

    override suspend fun exclusionsPaged(
        page: Int?,
        pageSize: Int?,
        sortKey: String?,
        sortDirection: SortDirection?,
    ): ImportListExclusionResourcePagingResource {
        before("exclusionsPaged")
        return exclusionPage(exclusions.toList(), page, pageSize, sortKey, sortDirection)
    }

    override suspend fun updateExclusion(
        id: String,
        body: ImportListExclusionResource,
    ): Response<ImportListExclusionResource> {
        before("updateExclusion")
        val idInt = id.toIntOrNull() ?: return notFound()
        val index = exclusions.indexOfFirst { it.id == idInt }
        if (index < 0) return notFound()
        val updated = body.copy(id = idInt)
        exclusions[index] = updated
        return Response.success(202, updated)
    }

    override suspend fun removeExclusion(id: Int): Response<Unit> {
        before("removeExclusion")
        if (exclusions.removeAll { it.id == id }) return Response.success(Unit)
        return notFound()
    }

    override suspend fun exclusion(id: Int): Response<ImportListExclusionResource> {
        before("exclusion")
        val found = exclusions.firstOrNull { it.id == id } ?: return notFound()
        return Response.success(found)
    }

    override suspend fun addExclusionsBulk(
        body: List<ImportListExclusionResource>,
    ): List<ImportListExclusionResource> {
        before("addExclusionsBulk")
        var id = nextId(exclusions.map { it.id })
        val added = body.map { resource ->
            val created = if (resource.id == 0) resource.copy(id = id) else resource
            if (resource.id == 0) id += 1
            exclusions += created
            created
        }
        return added
    }

    override suspend fun removeExclusionsBulk(body: ImportListExclusionBulkResource): JsonObject {
        before("removeExclusionsBulk")
        exclusions.removeAll { it.id in body.ids.orEmpty() }
        return emptyObject()
    }

    override suspend fun importListMovies(
        includeRecommendations: Boolean?,
        includeTrending: Boolean?,
        includePopular: Boolean?,
    ): List<ImportListMoviesResource> {
        before("importListMovies")
        return discoveredMovies.toList().filter { item ->
            (includeRecommendations != false || !item.isRecommendation) &&
                (includeTrending != false || !item.isTrending) &&
                (includePopular != false || !item.isPopular)
        }
    }

    override suspend fun addMovies(body: List<MovieResource>): List<MovieResource> {
        before("addMovies")
        var id = nextId(movies.map { it.id })
        return body.map { movie ->
            val withId = if (movie.id == 0) {
                val assigned = movie.copy(id = id)
                id += 1
                assigned
            } else {
                movie
            }
            val stored = withId.copy(added = withId.added ?: now())
            movies += stored
            stored
        }
    }

    private fun mergeTags(
        current: Set<Int>?,
        incoming: List<Int>?,
        applyTags: ApplyTags?,
    ): Set<Int>? = when (applyTags) {
        null -> current
        ApplyTags.ADD -> (current.orEmpty() + incoming.orEmpty()).toSet()
        ApplyTags.REMOVE -> current.orEmpty() - incoming.orEmpty().toSet()
        ApplyTags.REPLACE -> incoming?.toSet() ?: current
    }
}
