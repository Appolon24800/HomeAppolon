package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.CustomFilterResource
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResourcePagingResource
import dev.appolon.homelab.arr.api.sonarr.model.ImportListBulkResource
import dev.appolon.homelab.arr.api.sonarr.model.ImportListConfigResource
import dev.appolon.homelab.arr.api.sonarr.model.ImportListExclusionBulkResource
import dev.appolon.homelab.arr.api.sonarr.model.ImportListExclusionResource
import dev.appolon.homelab.arr.api.sonarr.model.ImportListExclusionResourcePagingResource
import dev.appolon.homelab.arr.api.sonarr.model.ImportListResource
import dev.appolon.homelab.arr.api.sonarr.model.SortDirection
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/**
 * Mutable single-test state for every discovery-group interface.
 * [latency] runs before [error], which can fail any operation before it mutates state.
 * Callbacks receive `Tag.method`. Wanted lists page over seeded episodes without
 * contacting an upstream list provider; `test` and `action` record calls only.
 */
class FakeSonarrDiscovery(
    var latency: suspend (String) -> Unit = {},
    var error: (String) -> Throwable? = { null }
) {
    val importListStore: MutableMap<Int, ImportListResource> = linkedMapOf()
    val importListSchemas: MutableList<ImportListResource> = mutableListOf()
    val importListExclusionStore: MutableMap<Int, ImportListExclusionResource> = linkedMapOf()
    val customFilterStore: MutableMap<Int, CustomFilterResource> = linkedMapOf()
    val missingEpisodes: MutableList<EpisodeResource> = mutableListOf()
    val cutoffEpisodes: MutableList<EpisodeResource> = mutableListOf()
    val testedLists: MutableList<ImportListResource> = mutableListOf()
    val testedAllCount: MutableList<Int> = mutableListOf()
    val actionCalls: MutableMap<String, MutableList<ImportListResource>> = linkedMapOf()
    var importListConfig: ImportListConfigResource = ImportListConfigResource()
    var forcedSaves: Int = 0
    var forcedTests: Int = 0

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

    private fun nextId(store: Map<Int, *>): Int =
        (store.keys.maxOrNull() ?: 0) + 1

    private fun wantedPage(
        rows: List<EpisodeResource>, page: Int?, pageSize: Int?,
        sortKey: String?, sortDirection: SortDirection?, monitored: Boolean?
    ): EpisodeResourcePagingResource {
        val visible = rows.filter { it.monitored == (monitored ?: true) }
        val sortedRows = sorted(visible, sortKey, sortDirection)
        return EpisodeResourcePagingResource(
            page = page ?: 1,
            pageSize = pageSize ?: 10,
            sortKey = sortKey,
            sortDirection = sortDirection ?: SortDirection.DEFAULT,
            totalRecords = sortedRows.size,
            records = paged(sortedRows, page, pageSize)
        )
    }

    val importList: ImportListApi = object : ImportListApi {
        override suspend fun list(): List<ImportListResource> {
            before("ImportList.list")
            return importListStore.values.toList()
        }

        override suspend fun create(
            forceSave: Boolean?, body: ImportListResource
        ): ImportListResource {
            before("ImportList.create")
            if (forceSave == true) forcedSaves++
            val id = if (body.id == 0) nextId(importListStore) else body.id
            val stored = body.copy(id = id)
            importListStore[id] = stored
            return stored
        }

        override suspend fun update(
            id: Int, forceSave: Boolean?, body: ImportListResource
        ): ImportListResource {
            before("ImportList.update")
            if (forceSave == true) forcedSaves++
            val current = importListStore[id] ?: missing(id)
            val stored = body.copy(id = current.id)
            importListStore[current.id] = stored
            return stored
        }

        override suspend fun delete(id: Int): Unit {
            before("ImportList.delete")
            importListStore.remove(id) ?: missing(id)
            return Unit
        }

        override suspend fun get(id: Int): Response<ImportListResource> {
            before("ImportList.get")
            val current = importListStore[id] ?: return notFound(id)
            return Response.success(current)
        }

        override suspend fun updateBulk(body: ImportListBulkResource): ImportListResource {
            before("ImportList.updateBulk")
            val ids = body.ids.orEmpty()
            var updated: ImportListResource? = null
            for (id in ids) {
                val current = importListStore[id] ?: continue
                updated = current.copy(
                    enableAutomaticAdd = body.enableAutomaticAdd
                        ?: current.enableAutomaticAdd,
                    rootFolderPath = body.rootFolderPath ?: current.rootFolderPath,
                    qualityProfileId = body.qualityProfileId ?: current.qualityProfileId,
                    tagExisting = body.tagExisting
                )
                importListStore[id] = updated
            }
            return updated ?: missing(ids.firstOrNull() ?: 0)
        }

        override suspend fun deleteBulk(body: ImportListBulkResource): Unit {
            before("ImportList.deleteBulk")
            body.ids.orEmpty().forEach(importListStore::remove)
            return Unit
        }

        override suspend fun schema(): List<ImportListResource> {
            before("ImportList.schema")
            return importListSchemas.toList()
        }

        override suspend fun test(forceTest: Boolean?, body: ImportListResource): Unit {
            before("ImportList.test")
            if (forceTest == true) forcedTests++
            testedLists.add(body)
            return Unit
        }

        override suspend fun testAll(): Unit {
            before("ImportList.testAll")
            testedAllCount.add(importListStore.size)
            return Unit
        }

        override suspend fun action(name: String, body: ImportListResource): Unit {
            before("ImportList.action")
            actionCalls.getOrPut(name) { mutableListOf() }.add(body)
            return Unit
        }
    }

    val importListConfigApi: ImportListConfigApi = object : ImportListConfigApi {
        override suspend fun list(): ImportListConfigResource {
            before("ImportListConfig.list")
            return importListConfig
        }

        override suspend fun update(
            id: String, body: ImportListConfigResource
        ): ImportListConfigResource {
            before("ImportListConfig.update")
            val stored = body.copy(id = importListConfig.id)
            importListConfig = stored
            return stored
        }

        override suspend fun get(id: Int): Response<ImportListConfigResource> {
            before("ImportListConfig.get")
            if (id != importListConfig.id) return notFound(id)
            return Response.success(importListConfig)
        }
    }

    val importListExclusion: ImportListExclusionApi = object : ImportListExclusionApi {
        override suspend fun list(): List<ImportListExclusionResource> {
            before("ImportListExclusion.list")
            return importListExclusionStore.values.toList()
        }

        override suspend fun create(
            body: ImportListExclusionResource
        ): ImportListExclusionResource {
            before("ImportListExclusion.create")
            val id = if (body.id == 0) nextId(importListExclusionStore) else body.id
            val stored = body.copy(id = id)
            importListExclusionStore[id] = stored
            return stored
        }

        override suspend fun paged(
            page: Int?,
            pageSize: Int?,
            sortKey: String?,
            sortDirection: SortDirection?
        ): ImportListExclusionResourcePagingResource {
            before("ImportListExclusion.paged")
            val rows = sorted(importListExclusionStore.values.toList(), sortKey, sortDirection)
            return ImportListExclusionResourcePagingResource(
                page = page ?: 1,
                pageSize = pageSize ?: 10,
                sortKey = sortKey,
                sortDirection = sortDirection ?: SortDirection.DEFAULT,
                totalRecords = rows.size,
                records = paged(rows, page, pageSize)
            )
        }

        override suspend fun update(
            id: String, body: ImportListExclusionResource
        ): ImportListExclusionResource {
            before("ImportListExclusion.update")
            val numeric = id.toIntOrNull() ?: missing(0)
            val current = importListExclusionStore[numeric] ?: missing(numeric)
            val stored = body.copy(id = current.id)
            importListExclusionStore[current.id] = stored
            return stored
        }

        override suspend fun delete(id: Int): Unit {
            before("ImportListExclusion.delete")
            importListExclusionStore.remove(id) ?: missing(id)
            return Unit
        }

        override suspend fun get(id: Int): Response<ImportListExclusionResource> {
            before("ImportListExclusion.get")
            val current = importListExclusionStore[id] ?: return notFound(id)
            return Response.success(current)
        }

        override suspend fun deleteBulk(body: ImportListExclusionBulkResource): Unit {
            before("ImportListExclusion.deleteBulk")
            body.ids.orEmpty().forEach(importListExclusionStore::remove)
            return Unit
        }
    }

    val customFilter: CustomFilterApi = object : CustomFilterApi {
        override suspend fun list(): List<CustomFilterResource> {
            before("CustomFilter.list")
            return customFilterStore.values.toList()
        }

        override suspend fun create(body: CustomFilterResource): CustomFilterResource {
            before("CustomFilter.create")
            val id = if (body.id == 0) nextId(customFilterStore) else body.id
            val stored = body.copy(id = id)
            customFilterStore[id] = stored
            return stored
        }

        override suspend fun update(
            id: String, body: CustomFilterResource
        ): CustomFilterResource {
            before("CustomFilter.update")
            val numeric = id.toIntOrNull() ?: missing(0)
            val current = customFilterStore[numeric] ?: missing(numeric)
            val stored = body.copy(id = current.id)
            customFilterStore[current.id] = stored
            return stored
        }

        override suspend fun delete(id: Int): Unit {
            before("CustomFilter.delete")
            customFilterStore.remove(id) ?: missing(id)
            return Unit
        }

        override suspend fun get(id: Int): Response<CustomFilterResource> {
            before("CustomFilter.get")
            val current = customFilterStore[id] ?: return notFound(id)
            return Response.success(current)
        }
    }

    val missingApi: MissingApi = object : MissingApi {
        override suspend fun list(
            page: Int?,
            pageSize: Int?,
            sortKey: String?,
            sortDirection: SortDirection?,
            includeSeries: Boolean?,
            includeImages: Boolean?,
            monitored: Boolean?
        ): EpisodeResourcePagingResource {
            before("Missing.list")
            return wantedPage(missingEpisodes, page, pageSize, sortKey, sortDirection, monitored)
        }

        override suspend fun get(id: Int): Response<EpisodeResource> {
            before("Missing.get")
            val episode = missingEpisodes.firstOrNull { it.id == id } ?: return notFound(id)
            return Response.success(episode)
        }
    }

    val cutoff: CutoffApi = object : CutoffApi {
        override suspend fun list(
            page: Int?,
            pageSize: Int?,
            sortKey: String?,
            sortDirection: SortDirection?,
            includeSeries: Boolean?,
            includeEpisodeFile: Boolean?,
            includeImages: Boolean?,
            monitored: Boolean?
        ): EpisodeResourcePagingResource {
            before("Cutoff.list")
            return wantedPage(cutoffEpisodes, page, pageSize, sortKey, sortDirection, monitored)
        }

        override suspend fun get(id: Int): Response<EpisodeResource> {
            before("Cutoff.get")
            val episode = cutoffEpisodes.firstOrNull { it.id == id } ?: return notFound(id)
            return Response.success(episode)
        }
    }

    private fun <T> notFound(id: Int): Response<T> = Response.error(
        404, "Missing resource $id".toResponseBody()
    )
}
