package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.AppProfileResource
import dev.appolon.homelab.arr.api.prowlarr.model.ApplicationBulkResource
import dev.appolon.homelab.arr.api.prowlarr.model.ApplicationResource
import dev.appolon.homelab.arr.api.prowlarr.model.ApplyTags
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerBulkResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerCategory
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerProxyResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerStatusResource
import dev.appolon.homelab.arr.api.prowlarr.model.TagDetailsResource
import dev.appolon.homelab.arr.api.prowlarr.model.TagResource
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/** One recorded fake invocation with its wire-shaped arguments. */
data class ProwlarrIndexersCall(val operation: String, val arguments: Map<String, Any?>)

/**
 * In-memory implementation of every indexer-group interface for UI tests.
 * Seed the state maps, then read through any interface: lists, CRUD and tag
 * details behave like the server, so screens can be driven end to end.
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
 * Tag detail notification usage belongs to another API group, so seed
 * [tagNotificationIds] instead. Configure and use the fake from one test
 * dispatcher.
 */
class FakeProwlarrIndexers(
    var latency: suspend (ProwlarrIndexersCall) -> Unit = {},
    var errorStatus: (ProwlarrIndexersCall) -> Int? = { null },
    var failure: (ProwlarrIndexersCall) -> Throwable? = { null },
) : IndexerApi,
    IndexerDefaultCategoriesApi,
    IndexerStatusApi,
    IndexerProxyApi,
    AppProfileApi,
    ApplicationApi,
    TagApi,
    TagDetailsApi {

    val calls: MutableList<ProwlarrIndexersCall> = mutableListOf()
    val indexers = mutableMapOf<Int, IndexerResource>()
    val indexerSchemas = mutableListOf<IndexerResource>()
    val indexerStatuses = mutableListOf<IndexerStatusResource>()
    val defaultCategories = mutableListOf<IndexerCategory>()
    val indexerProxies = mutableMapOf<Int, IndexerProxyResource>()
    val indexerProxySchemas = mutableListOf<IndexerProxyResource>()
    val appProfiles = mutableMapOf<Int, AppProfileResource>()
    var appProfileSchema: AppProfileResource = AppProfileResource()
    val applications = mutableMapOf<Int, ApplicationResource>()
    val applicationSchemas = mutableListOf<ApplicationResource>()
    val tags = mutableMapOf<Int, TagResource>()
    val tagNotificationIds = mutableMapOf<Int, List<Int>>()

    /** Ids handed to creates whose resource carries no id. */
    var nextId = 100

    private suspend fun gate(operation: String, vararg arguments: Pair<String, Any?>): Int? {
        val call = ProwlarrIndexersCall(operation, arguments.toMap())
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

    /** Gate for Response-returning calls: injected statuses ride the Response. */
    private suspend fun respond(
        operation: String,
        vararg arguments: Pair<String, Any?>,
    ): Response<Unit> = gate(operation, *arguments)
        ?.let { Response.error(it, "{}".toResponseBody()) }
        ?: Response.success(200, Unit)

    private fun httpError(status: Int): Nothing =
        throw HttpException(Response.error<Unit>(status, "{}".toResponseBody()))

    private fun pathId(id: String, fallback: Int): Int = id.toIntOrNull() ?: fallback

    private fun mergeTags(
        current: List<Int>?,
        incoming: List<Int>?,
        apply: ApplyTags,
    ): List<Int> = when (apply) {
        ApplyTags.ADD -> (current ?: emptyList()) + (incoming ?: emptyList())
        ApplyTags.REMOVE -> (current ?: emptyList()) - (incoming ?: emptyList()).toSet()
        ApplyTags.REPLACE -> incoming ?: current ?: emptyList()
    }.distinct()

    private fun <T : Any> stored(map: Map<Int, T>, id: Int): T = map[id] ?: httpError(404)

    private fun freeId(resourceId: Int): Int = resourceId.takeIf { it != 0 } ?: ++nextId

    private fun <R> upsert(
        map: MutableMap<Int, R>,
        pathId: String,
        resourceId: Int,
        resource: R,
    ): R {
        map[pathId(pathId, resourceId)] = resource
        return resource
    }

    override suspend fun getIndexers(): List<IndexerResource> {
        read("getIndexers")
        return indexers.values.toList()
    }

    override suspend fun getIndexer(id: Int): IndexerResource {
        read("getIndexer", "id" to id)
        return stored(indexers, id)
    }

    override suspend fun getIndexerSchemas(): List<IndexerResource> {
        read("getIndexerSchemas")
        return indexerSchemas.toList()
    }

    override suspend fun createIndexer(
        forceSave: Boolean?,
        resource: IndexerResource,
    ): IndexerResource {
        read("createIndexer", "forceSave" to forceSave, "resource" to resource)
        val stored = resource.copy(id = freeId(resource.id))
        indexers[stored.id] = stored
        return stored
    }

    override suspend fun updateIndexer(
        id: String,
        forceSave: Boolean?,
        resource: IndexerResource,
    ): IndexerResource {
        read("updateIndexer", "id" to id, "forceSave" to forceSave, "resource" to resource)
        return upsert(indexers, id, resource.id, resource)
    }

    override suspend fun updateIndexers(resource: IndexerBulkResource): IndexerResource {
        read("updateIndexers", "resource" to resource)
        val updated = (resource.ids ?: emptyList()).mapNotNull { id ->
            indexers[id]?.let { current ->
                indexers[id] = current.copy(
                    enable = resource.enable ?: current.enable,
                    appProfileId = resource.appProfileId ?: current.appProfileId,
                    priority = resource.priority ?: current.priority,
                    tags = mergeTags(current.tags, resource.tags, resource.applyTags),
                )
                indexers[id]
            }
        }
        return updated.firstOrNull() ?: IndexerResource()
    }

    override suspend fun deleteIndexer(id: Int): Response<Unit> {
        val status = gate("deleteIndexer", "id" to id)
        indexers.remove(id)
        return status?.let { Response.error(it, "{}".toResponseBody()) }
            ?: Response.success(200, Unit)
    }

    override suspend fun deleteIndexers(resource: IndexerBulkResource): Response<Unit> {
        val status = gate("deleteIndexers", "resource" to resource)
        if (status != null) return Response.error(status, "{}".toResponseBody())
        (resource.ids ?: emptyList()).forEach(indexers::remove)
        return Response.success(200, Unit)
    }

    override suspend fun testIndexer(
        forceTest: Boolean?,
        resource: IndexerResource,
    ): Response<Unit> = respond("testIndexer", "forceTest" to forceTest, "resource" to resource)

    override suspend fun testAllIndexers(): Response<Unit> = respond("testAllIndexers")

    override suspend fun indexerAction(name: String, resource: IndexerResource): Response<Unit> =
        respond("indexerAction", "name" to name, "resource" to resource)

    override suspend fun getCategories(): List<IndexerCategory> {
        read("getCategories")
        return defaultCategories.toList()
    }

    override suspend fun getIndexerStatuses(): List<IndexerStatusResource> {
        read("getIndexerStatuses")
        return indexerStatuses.toList()
    }

    override suspend fun getIndexerProxies(): List<IndexerProxyResource> {
        read("getIndexerProxies")
        return indexerProxies.values.toList()
    }

    override suspend fun getIndexerProxy(id: Int): IndexerProxyResource {
        read("getIndexerProxy", "id" to id)
        return stored(indexerProxies, id)
    }

    override suspend fun getIndexerProxySchemas(): List<IndexerProxyResource> {
        read("getIndexerProxySchemas")
        return indexerProxySchemas.toList()
    }

    override suspend fun createIndexerProxy(
        forceSave: Boolean?,
        resource: IndexerProxyResource,
    ): IndexerProxyResource {
        read("createIndexerProxy", "forceSave" to forceSave, "resource" to resource)
        val stored = resource.copy(id = freeId(resource.id))
        indexerProxies[stored.id] = stored
        return stored
    }

    override suspend fun updateIndexerProxy(
        id: String,
        forceSave: Boolean?,
        resource: IndexerProxyResource,
    ): IndexerProxyResource {
        read(
            "updateIndexerProxy",
            "id" to id,
            "forceSave" to forceSave,
            "resource" to resource,
        )
        return upsert(indexerProxies, id, resource.id, resource)
    }

    override suspend fun deleteIndexerProxy(id: Int): Response<Unit> {
        val status = gate("deleteIndexerProxy", "id" to id)
        indexerProxies.remove(id)
        return status?.let { Response.error(it, "{}".toResponseBody()) }
            ?: Response.success(200, Unit)
    }

    override suspend fun testIndexerProxy(
        forceTest: Boolean?,
        resource: IndexerProxyResource,
    ): Response<Unit> =
        respond("testIndexerProxy", "forceTest" to forceTest, "resource" to resource)

    override suspend fun testAllIndexerProxies(): Response<Unit> =
        respond("testAllIndexerProxies")

    override suspend fun indexerProxyAction(
        name: String,
        resource: IndexerProxyResource,
    ): Response<Unit> =
        respond("indexerProxyAction", "name" to name, "resource" to resource)

    override suspend fun getAppProfiles(): List<AppProfileResource> {
        read("getAppProfiles")
        return appProfiles.values.toList()
    }

    override suspend fun getAppProfile(id: Int): Response<AppProfileResource> {
        val status = gate("getAppProfile", "id" to id)
        if (status != null) return Response.error(status, "{}".toResponseBody())
        val stored = appProfiles[id] ?: return Response.error(404, "{}".toResponseBody())
        return Response.success(stored)
    }

    override suspend fun getAppProfileSchema(): AppProfileResource {
        read("getAppProfileSchema")
        return appProfileSchema
    }

    override suspend fun createAppProfile(resource: AppProfileResource): AppProfileResource {
        read("createAppProfile", "resource" to resource)
        val stored = resource.copy(id = freeId(resource.id))
        appProfiles[stored.id] = stored
        return stored
    }

    override suspend fun updateAppProfile(
        id: String,
        resource: AppProfileResource,
    ): AppProfileResource {
        read("updateAppProfile", "id" to id, "resource" to resource)
        return upsert(appProfiles, id, resource.id, resource)
    }

    override suspend fun deleteAppProfile(id: Int): Response<Unit> {
        val status = gate("deleteAppProfile", "id" to id)
        appProfiles.remove(id)
        return status?.let { Response.error(it, "{}".toResponseBody()) }
            ?: Response.success(200, Unit)
    }

    override suspend fun getApplications(): List<ApplicationResource> {
        read("getApplications")
        return applications.values.toList()
    }

    override suspend fun getApplication(id: Int): ApplicationResource {
        read("getApplication", "id" to id)
        return stored(applications, id)
    }

    override suspend fun getApplicationSchemas(): List<ApplicationResource> {
        read("getApplicationSchemas")
        return applicationSchemas.toList()
    }

    override suspend fun createApplication(
        forceSave: Boolean?,
        resource: ApplicationResource,
    ): ApplicationResource {
        read("createApplication", "forceSave" to forceSave, "resource" to resource)
        val stored = resource.copy(id = freeId(resource.id))
        applications[stored.id] = stored
        return stored
    }

    override suspend fun updateApplication(
        id: String,
        forceSave: Boolean?,
        resource: ApplicationResource,
    ): ApplicationResource {
        read("updateApplication", "id" to id, "forceSave" to forceSave, "resource" to resource)
        return upsert(applications, id, resource.id, resource)
    }

    override suspend fun updateApplications(resource: ApplicationBulkResource):
        ApplicationResource {
        read("updateApplications", "resource" to resource)
        val updated = (resource.ids ?: emptyList()).mapNotNull { id ->
            applications[id]?.let { current ->
                applications[id] = current.copy(
                    syncLevel = resource.syncLevel,
                    tags = mergeTags(current.tags, resource.tags, resource.applyTags),
                )
                applications[id]
            }
        }
        return updated.firstOrNull() ?: ApplicationResource()
    }

    override suspend fun deleteApplication(id: Int): Response<Unit> {
        val status = gate("deleteApplication", "id" to id)
        applications.remove(id)
        return status?.let { Response.error(it, "{}".toResponseBody()) }
            ?: Response.success(200, Unit)
    }

    override suspend fun deleteApplications(resource: ApplicationBulkResource): Response<Unit> {
        val status = gate("deleteApplications", "resource" to resource)
        if (status != null) return Response.error(status, "{}".toResponseBody())
        (resource.ids ?: emptyList()).forEach(applications::remove)
        return Response.success(200, Unit)
    }

    override suspend fun testApplication(
        forceTest: Boolean?,
        resource: ApplicationResource,
    ): Response<Unit> =
        respond("testApplication", "forceTest" to forceTest, "resource" to resource)

    override suspend fun testAllApplications(): Response<Unit> = respond("testAllApplications")

    override suspend fun applicationAction(
        name: String,
        resource: ApplicationResource,
    ): Response<Unit> = respond("applicationAction", "name" to name, "resource" to resource)

    override suspend fun getTags(): List<TagResource> {
        read("getTags")
        return tags.values.toList()
    }

    override suspend fun getTag(id: Int): TagResource {
        read("getTag", "id" to id)
        return stored(tags, id)
    }

    override suspend fun createTag(resource: TagResource): TagResource {
        read("createTag", "resource" to resource)
        val stored = resource.copy(id = freeId(resource.id))
        tags[stored.id] = stored
        return stored
    }

    override suspend fun updateTag(id: String, resource: TagResource): TagResource {
        read("updateTag", "id" to id, "resource" to resource)
        return upsert(tags, id, resource.id, resource)
    }

    override suspend fun deleteTag(id: Int): Response<Unit> {
        val status = gate("deleteTag", "id" to id)
        tags.remove(id)
        return status?.let { Response.error(it, "{}".toResponseBody()) }
            ?: Response.success(200, Unit)
    }

    override suspend fun getTagDetails(): List<TagDetailsResource> {
        read("getTagDetails")
        return tags.keys.map(::detailsFor)
    }

    override suspend fun getTagDetail(id: Int): TagDetailsResource {
        read("getTagDetail", "id" to id)
        if (!tags.containsKey(id)) httpError(404)
        return detailsFor(id)
    }

    private fun detailsFor(id: Int): TagDetailsResource = TagDetailsResource(
        id = id,
        label = tags[id]?.label,
        notificationIds = tagNotificationIds[id] ?: emptyList(),
        indexerIds = indexers.values.filter { id in it.tags.orEmpty() }.map { it.id },
        indexerProxyIds = indexerProxies.values
            .filter { id in it.tags.orEmpty() }
            .map { it.id },
        applicationIds = applications.values
            .filter { id in it.tags.orEmpty() }
            .map { it.id },
    )
}
