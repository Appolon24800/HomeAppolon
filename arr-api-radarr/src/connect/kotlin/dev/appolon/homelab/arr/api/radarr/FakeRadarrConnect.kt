package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.ApplyTags
import dev.appolon.homelab.arr.api.radarr.model.DownloadClientBulkResource
import dev.appolon.homelab.arr.api.radarr.model.DownloadClientConfigResource
import dev.appolon.homelab.arr.api.radarr.model.DownloadClientResource
import dev.appolon.homelab.arr.api.radarr.model.IndexerBulkResource
import dev.appolon.homelab.arr.api.radarr.model.IndexerConfigResource
import dev.appolon.homelab.arr.api.radarr.model.IndexerFlagResource
import dev.appolon.homelab.arr.api.radarr.model.IndexerResource
import dev.appolon.homelab.arr.api.radarr.model.NotificationResource
import dev.appolon.homelab.arr.api.radarr.model.ProviderTestAllResult
import dev.appolon.homelab.arr.api.radarr.model.RemotePathMappingResource
import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

/**
 * Mutable single-test state implementing every connect-group interface.
 * Inject a suspending [sleep] such as coroutine delay to use virtual time without a runtime
 * coroutine dependency. The default sleep blocks the calling thread, so UI tests should inject it.
 * This fake contacts no download clients, indexers, or notification services; provider
 * interactions such as [DownloadClientApi.testDownloadClient] and [IndexerApi.indexerAction]
 * are only recorded. [errorFor] receives the operation name and runs before any state mutation.
 */
class FakeRadarrConnect(
    var latencyMs: Long = 0,
    var errorFor: (String) -> Throwable? = { null },
    private val sleep: suspend (Long) -> Unit = { Thread.sleep(it) },
) : DownloadClientApi, DownloadClientConfigApi, IndexerApi, IndexerConfigApi, IndexerFlagApi,
    NotificationApi, RemotePathMappingApi {

    val downloadClients = mutableListOf<DownloadClientResource>()
    val downloadClientSchemas = mutableListOf<DownloadClientResource>()
    var downloadClientConfig = DownloadClientConfigResource()

    val indexers = mutableListOf<IndexerResource>()
    val indexerSchemas = mutableListOf<IndexerResource>()
    var indexerConfig = IndexerConfigResource()
    val indexerFlags = mutableListOf<IndexerFlagResource>()

    val notifications = mutableListOf<NotificationResource>()
    val notificationSchemas = mutableListOf<NotificationResource>()

    val remotePathMappings = mutableListOf<RemotePathMappingResource>()

    /** Provider interactions the fake cannot perform, kept for assertions. */
    val testedDownloadClients = mutableListOf<DownloadClientResource>()
    val testedIndexers = mutableListOf<IndexerResource>()
    val testedNotifications = mutableListOf<NotificationResource>()
    val downloadClientActions = mutableListOf<Pair<String, DownloadClientResource>>()
    val indexerActions = mutableListOf<Pair<String, IndexerResource>>()
    val notificationActions = mutableListOf<Pair<String, NotificationResource>>()

    private suspend fun before(operation: String) {
        require(latencyMs >= 0) { "Latency must be nonnegative" }
        if (latencyMs > 0) sleep(latencyMs)
        errorFor(operation)?.let { throw it }
    }

    private fun <T> notFound(): Response<T> = Response.error(404, "{}".toResponseBody())

    private fun emptyObject(): JsonObject = JsonObject(emptyMap())

    private fun nextId(ids: Iterable<Int>): Int = (ids.maxOrNull() ?: 0) + 1

    private fun mergedTags(current: Set<Int>, tags: List<Int>, apply: ApplyTags?): Set<Int> =
        when (apply) {
            ApplyTags.ADD -> current + tags
            ApplyTags.REMOVE -> current - tags.toSet()
            else -> tags.toSet()
        }

    override suspend fun downloadClients(): List<DownloadClientResource> {
        before("downloadClients")
        return downloadClients.toList()
    }

    override suspend fun createDownloadClient(
        forceSave: Boolean?,
        body: DownloadClientResource,
    ): Response<DownloadClientResource> {
        before("createDownloadClient")
        val created = body.copy(id = nextId(downloadClients.map { it.id }))
        downloadClients += created
        return Response.success(201, created)
    }

    override suspend fun updateDownloadClient(
        id: Int,
        forceSave: Boolean?,
        body: DownloadClientResource,
    ): Response<DownloadClientResource> {
        before("updateDownloadClient")
        val index = downloadClients.indexOfFirst { it.id == id }
        if (index < 0) return notFound()
        val updated = body.copy(id = id)
        downloadClients[index] = updated
        return Response.success(202, updated)
    }

    override suspend fun removeDownloadClient(id: Int): JsonObject {
        before("removeDownloadClient")
        downloadClients.removeAll { it.id == id }
        return emptyObject()
    }

    override suspend fun downloadClient(id: Int): Response<DownloadClientResource> {
        before("downloadClient")
        val found = downloadClients.firstOrNull { it.id == id } ?: return notFound()
        return Response.success(found)
    }

    override suspend fun updateDownloadClientBulk(
        body: DownloadClientBulkResource,
    ): Response<List<DownloadClientResource>> {
        before("updateDownloadClientBulk")
        val ids = body.ids.orEmpty()
        downloadClients.replaceAll { client ->
            if (client.id !in ids) {
                client
            } else {
                client.copy(
                    tags = mergedTags(
                        client.tags.orEmpty(),
                        body.tags.orEmpty(),
                        body.applyTags,
                    ),
                    enable = body.enable ?: client.enable,
                    priority = body.priority ?: client.priority,
                    removeCompletedDownloads = body.removeCompletedDownloads
                        ?: client.removeCompletedDownloads,
                    removeFailedDownloads = body.removeFailedDownloads
                        ?: client.removeFailedDownloads,
                )
            }
        }
        return Response.success(downloadClients.filter { it.id in ids })
    }

    override suspend fun removeDownloadClientBulk(
        body: DownloadClientBulkResource,
    ): JsonObject {
        before("removeDownloadClientBulk")
        downloadClients.removeAll { it.id in body.ids.orEmpty() }
        return emptyObject()
    }

    override suspend fun downloadClientSchema(): List<DownloadClientResource> {
        before("downloadClientSchema")
        return downloadClientSchemas.toList()
    }

    override suspend fun testDownloadClient(
        forceTest: Boolean?,
        body: DownloadClientResource,
    ): Response<Unit> {
        before("testDownloadClient")
        testedDownloadClients += body
        return Response.success(204, Unit)
    }

    override suspend fun testAllDownloadClients(): Response<List<ProviderTestAllResult>> {
        before("testAllDownloadClients")
        return Response.success(
            downloadClients.filter { it.enable }.map {
                ProviderTestAllResult(id = it.id, isValid = true, validationFailures = null)
            },
        )
    }

    override suspend fun downloadClientAction(
        name: String,
        body: DownloadClientResource,
    ): Response<Unit> {
        before("downloadClientAction")
        downloadClientActions += name to body
        return Response.success(Unit)
    }

    override suspend fun downloadClientConfig(): DownloadClientConfigResource {
        before("downloadClientConfig")
        return downloadClientConfig
    }

    /** Singleton config rows always exist on the server, so any valid id is accepted. */
    override suspend fun updateDownloadClientConfig(
        id: String,
        body: DownloadClientConfigResource,
    ): Response<DownloadClientConfigResource> {
        before("updateDownloadClientConfig")
        val idInt = id.toIntOrNull() ?: return notFound()
        downloadClientConfig = body.copy(id = idInt)
        return Response.success(202, downloadClientConfig)
    }

    override suspend fun downloadClientConfigById(
        id: Int,
    ): Response<DownloadClientConfigResource> {
        before("downloadClientConfigById")
        if (downloadClientConfig.id != id) return notFound()
        return Response.success(downloadClientConfig)
    }

    override suspend fun indexers(): List<IndexerResource> {
        before("indexers")
        return indexers.toList()
    }

    override suspend fun createIndexer(
        forceSave: Boolean?,
        body: IndexerResource,
    ): Response<IndexerResource> {
        before("createIndexer")
        val created = body.copy(id = nextId(indexers.map { it.id }))
        indexers += created
        return Response.success(201, created)
    }

    override suspend fun updateIndexer(
        id: Int,
        forceSave: Boolean?,
        body: IndexerResource,
    ): Response<IndexerResource> {
        before("updateIndexer")
        val index = indexers.indexOfFirst { it.id == id }
        if (index < 0) return notFound()
        val updated = body.copy(id = id)
        indexers[index] = updated
        return Response.success(202, updated)
    }

    override suspend fun removeIndexer(id: Int): JsonObject {
        before("removeIndexer")
        indexers.removeAll { it.id == id }
        return emptyObject()
    }

    override suspend fun indexer(id: Int): Response<IndexerResource> {
        before("indexer")
        val found = indexers.firstOrNull { it.id == id } ?: return notFound()
        return Response.success(found)
    }

    override suspend fun updateIndexerBulk(
        body: IndexerBulkResource,
    ): Response<List<IndexerResource>> {
        before("updateIndexerBulk")
        val ids = body.ids.orEmpty()
        indexers.replaceAll { indexer ->
            if (indexer.id !in ids) {
                indexer
            } else {
                indexer.copy(
                    tags = mergedTags(
                        indexer.tags.orEmpty(),
                        body.tags.orEmpty(),
                        body.applyTags,
                    ),
                    enableRss = body.enableRss ?: indexer.enableRss,
                    enableAutomaticSearch = body.enableAutomaticSearch
                        ?: indexer.enableAutomaticSearch,
                    enableInteractiveSearch = body.enableInteractiveSearch
                        ?: indexer.enableInteractiveSearch,
                    priority = body.priority ?: indexer.priority,
                )
            }
        }
        return Response.success(indexers.filter { it.id in ids })
    }

    override suspend fun removeIndexerBulk(body: IndexerBulkResource): JsonObject {
        before("removeIndexerBulk")
        indexers.removeAll { it.id in body.ids.orEmpty() }
        return emptyObject()
    }

    override suspend fun indexerSchema(): List<IndexerResource> {
        before("indexerSchema")
        return indexerSchemas.toList()
    }

    override suspend fun testIndexer(
        forceTest: Boolean?,
        body: IndexerResource,
    ): Response<Unit> {
        before("testIndexer")
        testedIndexers += body
        return Response.success(204, Unit)
    }

    override suspend fun testAllIndexers(): Response<List<ProviderTestAllResult>> {
        before("testAllIndexers")
        return Response.success(
            indexers.filter { it.enableRss || it.enableAutomaticSearch }.map {
                ProviderTestAllResult(id = it.id, isValid = true, validationFailures = null)
            },
        )
    }

    override suspend fun indexerAction(name: String, body: IndexerResource): Response<Unit> {
        before("indexerAction")
        indexerActions += name to body
        return Response.success(Unit)
    }

    override suspend fun indexerConfig(): IndexerConfigResource {
        before("indexerConfig")
        return indexerConfig
    }

    /** Singleton config rows always exist on the server, so any valid id is accepted. */
    override suspend fun updateIndexerConfig(
        id: String,
        body: IndexerConfigResource,
    ): Response<IndexerConfigResource> {
        before("updateIndexerConfig")
        val idInt = id.toIntOrNull() ?: return notFound()
        indexerConfig = body.copy(id = idInt)
        return Response.success(202, indexerConfig)
    }

    override suspend fun indexerConfigById(id: Int): Response<IndexerConfigResource> {
        before("indexerConfigById")
        if (indexerConfig.id != id) return notFound()
        return Response.success(indexerConfig)
    }

    override suspend fun indexerFlags(): List<IndexerFlagResource> {
        before("indexerFlags")
        return indexerFlags.toList()
    }

    override suspend fun notifications(): List<NotificationResource> {
        before("notifications")
        return notifications.toList()
    }

    override suspend fun createNotification(
        forceSave: Boolean?,
        body: NotificationResource,
    ): Response<NotificationResource> {
        before("createNotification")
        val created = body.copy(id = nextId(notifications.map { it.id }))
        notifications += created
        return Response.success(201, created)
    }

    override suspend fun updateNotification(
        id: Int,
        forceSave: Boolean?,
        body: NotificationResource,
    ): Response<NotificationResource> {
        before("updateNotification")
        val index = notifications.indexOfFirst { it.id == id }
        if (index < 0) return notFound()
        val updated = body.copy(id = id)
        notifications[index] = updated
        return Response.success(202, updated)
    }

    override suspend fun removeNotification(id: Int): JsonObject {
        before("removeNotification")
        notifications.removeAll { it.id == id }
        return emptyObject()
    }

    override suspend fun notification(id: Int): Response<NotificationResource> {
        before("notification")
        val found = notifications.firstOrNull { it.id == id } ?: return notFound()
        return Response.success(found)
    }

    override suspend fun notificationSchema(): List<NotificationResource> {
        before("notificationSchema")
        return notificationSchemas.toList()
    }

    override suspend fun testNotification(
        forceTest: Boolean?,
        body: NotificationResource,
    ): Response<Unit> {
        before("testNotification")
        testedNotifications += body
        return Response.success(204, Unit)
    }

    override suspend fun testAllNotifications(): Response<List<ProviderTestAllResult>> {
        before("testAllNotifications")
        return Response.success(
            notifications.map {
                ProviderTestAllResult(id = it.id, isValid = true, validationFailures = null)
            },
        )
    }

    override suspend fun notificationAction(
        name: String,
        body: NotificationResource,
    ): Response<Unit> {
        before("notificationAction")
        notificationActions += name to body
        return Response.success(Unit)
    }

    override suspend fun createRemotePathMapping(
        body: RemotePathMappingResource,
    ): Response<RemotePathMappingResource> {
        before("createRemotePathMapping")
        val created = body.copy(id = nextId(remotePathMappings.map { it.id }))
        remotePathMappings += created
        return Response.success(201, created)
    }

    override suspend fun remotePathMappings(): List<RemotePathMappingResource> {
        before("remotePathMappings")
        return remotePathMappings.toList()
    }

    override suspend fun removeRemotePathMapping(id: Int) {
        before("removeRemotePathMapping")
        remotePathMappings.removeAll { it.id == id }
    }

    override suspend fun updateRemotePathMapping(
        id: String,
        body: RemotePathMappingResource,
    ): Response<RemotePathMappingResource> {
        before("updateRemotePathMapping")
        val idInt = id.toIntOrNull() ?: return notFound()
        val index = remotePathMappings.indexOfFirst { it.id == idInt }
        if (index < 0) return notFound()
        val updated = body.copy(id = idInt)
        remotePathMappings[index] = updated
        return Response.success(updated)
    }

    override suspend fun remotePathMapping(
        id: Int,
    ): Response<RemotePathMappingResource> {
        before("remotePathMapping")
        val found = remotePathMappings.firstOrNull { it.id == id } ?: return notFound()
        return Response.success(found)
    }

}
