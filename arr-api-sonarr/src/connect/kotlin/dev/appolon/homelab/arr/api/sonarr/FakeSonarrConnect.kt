package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.ApplyTags
import dev.appolon.homelab.arr.api.sonarr.model.DownloadClientBulkResource
import dev.appolon.homelab.arr.api.sonarr.model.DownloadClientConfigResource
import dev.appolon.homelab.arr.api.sonarr.model.DownloadClientResource
import dev.appolon.homelab.arr.api.sonarr.model.IndexerBulkResource
import dev.appolon.homelab.arr.api.sonarr.model.IndexerConfigResource
import dev.appolon.homelab.arr.api.sonarr.model.IndexerFlagResource
import dev.appolon.homelab.arr.api.sonarr.model.IndexerResource
import dev.appolon.homelab.arr.api.sonarr.model.NotificationResource
import dev.appolon.homelab.arr.api.sonarr.model.RemotePathMappingResource
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

/**
 * Mutable, single-test connect state.
 * Supply [latency] to delay calls and [error] to inject failures.
 * The callbacks receive the OpenAPI tag and method name, such as `Indexer.list`.
 * Network actions have no side effects beyond the stores below.
 */
class FakeSonarrConnect(
    var latency: suspend (String) -> Unit = {},
    var error: (String) -> Throwable? = { null }
) {
    val downloadClientStore: MutableMap<Int, DownloadClientResource> = linkedMapOf()
    val downloadClientSchemas: MutableList<DownloadClientResource> = mutableListOf()
    var downloadClientConfig: DownloadClientConfigResource = DownloadClientConfigResource()
    val indexerStore: MutableMap<Int, IndexerResource> = linkedMapOf()
    val indexerSchemas: MutableList<IndexerResource> = mutableListOf()
    var indexerConfig: IndexerConfigResource = IndexerConfigResource()
    val indexerFlags: MutableList<IndexerFlagResource> = mutableListOf()
    val notificationStore: MutableMap<Int, NotificationResource> = linkedMapOf()
    val notificationSchemas: MutableList<NotificationResource> = mutableListOf()
    val remotePathMappingStore: MutableMap<Int, RemotePathMappingResource> = linkedMapOf()

    var forcedSaves: Int = 0
        private set
    var forcedTests: Int = 0
        private set
    val testedDownloadClients: MutableList<DownloadClientResource> = mutableListOf()
    val testedIndexers: MutableList<IndexerResource> = mutableListOf()
    val testedNotifications: MutableList<NotificationResource> = mutableListOf()
    val testedAllCounts: MutableList<Int> = mutableListOf()
    val actionCalls: MutableMap<String, MutableList<String>> = linkedMapOf()

    private suspend fun before(operation: String) {
        latency(operation)
        error(operation)?.let { throw it }
    }

    private fun nextId(store: Map<Int, *>): Int = (store.keys.maxOrNull() ?: 0) + 1

    private fun missing(id: Int): Nothing = throw retrofit2.HttpException(
        Response.error<Any>(404, "Missing resource $id".toResponseBody())
    )

    private fun <T> notFound(id: Int): Response<T> =
        Response.error(404, "Missing resource $id".toResponseBody())

    val downloadClientApi: DownloadClientApi = object : DownloadClientApi {
        override suspend fun list(): List<DownloadClientResource> {
            before("DownloadClient.list")
            return downloadClientStore.values.toList()
        }

        override suspend fun create(
            forceSave: Boolean?,
            body: DownloadClientResource
        ): DownloadClientResource {
            before("DownloadClient.create")
            if (forceSave == true) forcedSaves++
            val id = if (body.id == 0) nextId(downloadClientStore) else body.id
            return body.copy(id = id).also { downloadClientStore[id] = it }
        }

        override suspend fun update(
            id: Int,
            forceSave: Boolean?,
            body: DownloadClientResource
        ): DownloadClientResource {
            before("DownloadClient.update")
            if (forceSave == true) forcedSaves++
            val current = downloadClientStore[id] ?: missing(id)
            return body.copy(id = current.id).also { downloadClientStore[current.id] = it }
        }

        override suspend fun delete(id: Int): Unit {
            before("DownloadClient.delete")
            downloadClientStore.remove(id) ?: missing(id)
            return Unit
        }

        override suspend fun get(id: Int): Response<DownloadClientResource> {
            before("DownloadClient.get")
            val current = downloadClientStore[id] ?: return notFound(id)
            return Response.success(current)
        }

        override suspend fun updateBulk(
            body: DownloadClientBulkResource
        ): DownloadClientResource {
            before("DownloadClient.updateBulk")
            val ids = body.ids.orEmpty()
            var updated: DownloadClientResource? = null
            for (id in ids) {
                val current = downloadClientStore[id] ?: continue
                updated = current.copy(
                    enable = body.enable ?: current.enable,
                    priority = body.priority ?: current.priority,
                    removeCompletedDownloads = body.removeCompletedDownloads
                        ?: current.removeCompletedDownloads,
                    removeFailedDownloads = body.removeFailedDownloads
                        ?: current.removeFailedDownloads,
                    tags = applyTags(current.tags, body.tags, body.applyTags)
                )
                downloadClientStore[id] = updated
            }
            return updated ?: missing(ids.firstOrNull() ?: 0)
        }

        override suspend fun deleteBulk(body: DownloadClientBulkResource): Unit {
            before("DownloadClient.deleteBulk")
            body.ids.orEmpty().forEach(downloadClientStore::remove)
            return Unit
        }

        override suspend fun schema(): List<DownloadClientResource> {
            before("DownloadClient.schema")
            return downloadClientSchemas.toList()
        }

        override suspend fun test(
            forceTest: Boolean?,
            body: DownloadClientResource
        ): Unit {
            before("DownloadClient.test")
            if (forceTest == true) forcedTests++
            testedDownloadClients.add(body)
            return Unit
        }

        override suspend fun testAll(): Unit {
            before("DownloadClient.testAll")
            testedAllCounts.add(downloadClientStore.size)
            return Unit
        }

        override suspend fun action(
            name: String,
            body: DownloadClientResource
        ): Unit {
            before("DownloadClient.action")
            actionCalls.getOrPut("DownloadClient.$name") { mutableListOf() }.add(name)
            return Unit
        }
    }

    val downloadClientConfigApi: DownloadClientConfigApi = object : DownloadClientConfigApi {
        override suspend fun get(): DownloadClientConfigResource {
            before("DownloadClientConfig.get")
            return downloadClientConfig
        }

        override suspend fun update(
            id: String,
            body: DownloadClientConfigResource
        ): DownloadClientConfigResource {
            before("DownloadClientConfig.update")
            downloadClientConfig = body.copy(id = id.toIntOrNull() ?: body.id)
            return downloadClientConfig
        }

        override suspend fun get(id: Int): Response<DownloadClientConfigResource> {
            before("DownloadClientConfig.get")
            val current = downloadClientConfig
            if (current.id != id) return notFound(id)
            return Response.success(current)
        }
    }

    val indexerApi: IndexerApi = object : IndexerApi {
        override suspend fun list(): List<IndexerResource> {
            before("Indexer.list")
            return indexerStore.values.toList()
        }

        override suspend fun create(
            forceSave: Boolean?,
            body: IndexerResource
        ): IndexerResource {
            before("Indexer.create")
            if (forceSave == true) forcedSaves++
            val id = if (body.id == 0) nextId(indexerStore) else body.id
            return body.copy(id = id).also { indexerStore[id] = it }
        }

        override suspend fun update(
            id: Int,
            forceSave: Boolean?,
            body: IndexerResource
        ): IndexerResource {
            before("Indexer.update")
            if (forceSave == true) forcedSaves++
            val current = indexerStore[id] ?: missing(id)
            return body.copy(id = current.id).also { indexerStore[current.id] = it }
        }

        override suspend fun delete(id: Int): Unit {
            before("Indexer.delete")
            indexerStore.remove(id) ?: missing(id)
            return Unit
        }

        override suspend fun get(id: Int): Response<IndexerResource> {
            before("Indexer.get")
            val current = indexerStore[id] ?: return notFound(id)
            return Response.success(current)
        }

        override suspend fun updateBulk(body: IndexerBulkResource): IndexerResource {
            before("Indexer.updateBulk")
            val ids = body.ids.orEmpty()
            var updated: IndexerResource? = null
            for (id in ids) {
                val current = indexerStore[id] ?: continue
                updated = current.copy(
                    enableRss = body.enableRss ?: current.enableRss,
                    enableAutomaticSearch = body.enableAutomaticSearch
                        ?: current.enableAutomaticSearch,
                    enableInteractiveSearch = body.enableInteractiveSearch
                        ?: current.enableInteractiveSearch,
                    priority = body.priority ?: current.priority,
                    seasonSearchMaximumSingleEpisodeAge = body.seasonSearchMaximumSingleEpisodeAge
                        ?: current.seasonSearchMaximumSingleEpisodeAge,
                    tags = applyTags(current.tags, body.tags, body.applyTags)
                )
                indexerStore[id] = updated
            }
            return updated ?: missing(ids.firstOrNull() ?: 0)
        }

        override suspend fun deleteBulk(body: IndexerBulkResource): Unit {
            before("Indexer.deleteBulk")
            body.ids.orEmpty().forEach(indexerStore::remove)
            return Unit
        }

        override suspend fun schema(): List<IndexerResource> {
            before("Indexer.schema")
            return indexerSchemas.toList()
        }

        override suspend fun test(
            forceTest: Boolean?,
            body: IndexerResource
        ): Unit {
            before("Indexer.test")
            if (forceTest == true) forcedTests++
            testedIndexers.add(body)
            return Unit
        }

        override suspend fun testAll(): Unit {
            before("Indexer.testAll")
            testedAllCounts.add(indexerStore.size)
            return Unit
        }

        override suspend fun action(
            name: String,
            body: IndexerResource
        ): Unit {
            before("Indexer.action")
            actionCalls.getOrPut("Indexer.$name") { mutableListOf() }.add(name)
            return Unit
        }
    }

    val indexerConfigApi: IndexerConfigApi = object : IndexerConfigApi {
        override suspend fun get(): IndexerConfigResource {
            before("IndexerConfig.get")
            return indexerConfig
        }

        override suspend fun update(
            id: String,
            body: IndexerConfigResource
        ): IndexerConfigResource {
            before("IndexerConfig.update")
            indexerConfig = body.copy(id = id.toIntOrNull() ?: body.id)
            return indexerConfig
        }

        override suspend fun get(id: Int): Response<IndexerConfigResource> {
            before("IndexerConfig.get")
            val current = indexerConfig
            if (current.id != id) return notFound(id)
            return Response.success(current)
        }
    }

    val indexerFlagApi: IndexerFlagApi = object : IndexerFlagApi {
        override suspend fun list(): List<IndexerFlagResource> {
            before("IndexerFlag.list")
            return indexerFlags.toList()
        }
    }

    val notificationApi: NotificationApi = object : NotificationApi {
        override suspend fun list(): List<NotificationResource> {
            before("Notification.list")
            return notificationStore.values.toList()
        }

        override suspend fun create(
            forceSave: Boolean?,
            body: NotificationResource
        ): NotificationResource {
            before("Notification.create")
            if (forceSave == true) forcedSaves++
            val id = if (body.id == 0) nextId(notificationStore) else body.id
            return body.copy(id = id).also { notificationStore[id] = it }
        }

        override suspend fun update(
            id: Int,
            forceSave: Boolean?,
            body: NotificationResource
        ): NotificationResource {
            before("Notification.update")
            if (forceSave == true) forcedSaves++
            val current = notificationStore[id] ?: missing(id)
            return body.copy(id = current.id).also { notificationStore[current.id] = it }
        }

        override suspend fun delete(id: Int): Unit {
            before("Notification.delete")
            notificationStore.remove(id) ?: missing(id)
            return Unit
        }

        override suspend fun get(id: Int): Response<NotificationResource> {
            before("Notification.get")
            val current = notificationStore[id] ?: return notFound(id)
            return Response.success(current)
        }

        override suspend fun schema(): List<NotificationResource> {
            before("Notification.schema")
            return notificationSchemas.toList()
        }

        override suspend fun test(
            forceTest: Boolean?,
            body: NotificationResource
        ): Unit {
            before("Notification.test")
            if (forceTest == true) forcedTests++
            testedNotifications.add(body)
            return Unit
        }

        override suspend fun testAll(): Unit {
            before("Notification.testAll")
            testedAllCounts.add(notificationStore.size)
            return Unit
        }

        override suspend fun action(
            name: String,
            body: NotificationResource
        ): Unit {
            before("Notification.action")
            actionCalls.getOrPut("Notification.$name") { mutableListOf() }.add(name)
            return Unit
        }
    }

    val remotePathMappingApi: RemotePathMappingApi = object : RemotePathMappingApi {
        override suspend fun create(
            body: RemotePathMappingResource
        ): RemotePathMappingResource {
            before("RemotePathMapping.create")
            val id = if (body.id == 0) nextId(remotePathMappingStore) else body.id
            return body.copy(id = id).also { remotePathMappingStore[id] = it }
        }

        override suspend fun list(): List<RemotePathMappingResource> {
            before("RemotePathMapping.list")
            return remotePathMappingStore.values.toList()
        }

        override suspend fun delete(id: Int): Unit {
            before("RemotePathMapping.delete")
            remotePathMappingStore.remove(id) ?: missing(id)
            return Unit
        }

        override suspend fun update(
            id: String,
            body: RemotePathMappingResource
        ): RemotePathMappingResource {
            before("RemotePathMapping.update")
            val key = id.toIntOrNull() ?: missing(0)
            val current = remotePathMappingStore[key] ?: missing(key)
            return body.copy(id = current.id).also { remotePathMappingStore[current.id] = it }
        }

        override suspend fun get(id: Int): Response<RemotePathMappingResource> {
            before("RemotePathMapping.get")
            val current = remotePathMappingStore[id] ?: return notFound(id)
            return Response.success(current)
        }
    }

    private fun applyTags(
        current: Set<Int>?,
        incoming: List<Int>?,
        mode: ApplyTags
    ): Set<Int>? {
        if (incoming == null) return current
        return when (mode) {
            ApplyTags.ADD -> current.orEmpty() + incoming
            ApplyTags.REMOVE -> current.orEmpty() - incoming.toSet()
            ApplyTags.REPLACE -> incoming.toSet()
        }
    }
}
