package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.ApplyTags
import dev.appolon.homelab.arr.api.lidarr.model.AutoTaggingResource
import dev.appolon.homelab.arr.api.lidarr.model.BackupResource
import dev.appolon.homelab.arr.api.lidarr.model.DiskSpaceResource
import dev.appolon.homelab.arr.api.lidarr.model.DownloadClientBulkResource
import dev.appolon.homelab.arr.api.lidarr.model.DownloadClientConfigResource
import dev.appolon.homelab.arr.api.lidarr.model.DownloadClientResource
import dev.appolon.homelab.arr.api.lidarr.model.HealthResource
import dev.appolon.homelab.arr.api.lidarr.model.HostConfigResource
import dev.appolon.homelab.arr.api.lidarr.model.IndexerBulkResource
import dev.appolon.homelab.arr.api.lidarr.model.IndexerConfigResource
import dev.appolon.homelab.arr.api.lidarr.model.IndexerFlagResource
import dev.appolon.homelab.arr.api.lidarr.model.IndexerResource
import dev.appolon.homelab.arr.api.lidarr.model.LocalizationResource
import dev.appolon.homelab.arr.api.lidarr.model.LogFileResource
import dev.appolon.homelab.arr.api.lidarr.model.LogResource
import dev.appolon.homelab.arr.api.lidarr.model.LogResourcePagingResource
import dev.appolon.homelab.arr.api.lidarr.model.MediaManagementConfigResource
import dev.appolon.homelab.arr.api.lidarr.model.MetadataResource
import dev.appolon.homelab.arr.api.lidarr.model.NamingConfigResource
import dev.appolon.homelab.arr.api.lidarr.model.NotificationResource
import dev.appolon.homelab.arr.api.lidarr.model.RemotePathMappingResource
import dev.appolon.homelab.arr.api.lidarr.model.RootFolderResource
import dev.appolon.homelab.arr.api.lidarr.model.SortDirection
import dev.appolon.homelab.arr.api.lidarr.model.SystemResource
import dev.appolon.homelab.arr.api.lidarr.model.TagDetailsResource
import dev.appolon.homelab.arr.api.lidarr.model.TagResource
import dev.appolon.homelab.arr.api.lidarr.model.TaskResource
import dev.appolon.homelab.arr.api.lidarr.model.UiConfigResource
import dev.appolon.homelab.arr.api.lidarr.model.UpdateResource
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/**
 * In-memory implementations of every system, settings and connect client.
 * Seed the collections before UI tests. [latency] can suspend on the test
 * scheduler and [error] runs before each operation, keyed by operation name.
 *
 * Missing entity ids surface as HttpException 404 like the real server. Raw
 * JSON endpoints without a spec schema hold their payload in a JsonObject the
 * test seeds directly.
 */
class FakeLidarrSystem(
    var latency: suspend (operation: String) -> Unit = {},
    var error: (operation: String) -> Throwable? = { null },
) : ApiInfoApi, AuthenticationApi, BackupApi, DiskSpaceApi, HealthApi, LogApi, LogFileApi,
    SystemApi, TaskApi, UpdateApi, UpdateLogFileApi, LocalizationApi, AutoTaggingApi,
    HostConfigApi, MediaManagementConfigApi, NamingConfigApi, UiConfigApi, MetadataApi,
    NotificationApi, DownloadClientApi, DownloadClientConfigApi, IndexerApi, IndexerConfigApi,
    IndexerFlagApi, RemotePathMappingApi, RootFolderApi, TagApi, TagDetailsApi, FileSystemApi {
    var apiInfo: JsonObject = buildJsonObject {
        put("currentAPIVersion", 1)
    }
    var loginCount: Int = 0
        private set
    var logoutCount: Int = 0
        private set
    val backups: MutableMap<Int, BackupResource> = linkedMapOf()
    val restoredBackupIds: MutableList<Int> = mutableListOf()
    var restoreUploadCount: Int = 0
        private set
    val diskSpace: MutableList<DiskSpaceResource> = mutableListOf()
    val health: MutableList<HealthResource> = mutableListOf()
    val logRecords: MutableList<LogResource> = mutableListOf()
    val logFiles: MutableList<LogFileResource> = mutableListOf()
    /** Raw log text by filename for the file content endpoint. */
    val logFileContents: MutableMap<String, String> = linkedMapOf()
    var systemStatus: SystemResource = SystemResource(appName = "Lidarr")
    var routes: JsonObject = buildJsonObject { put("routes", 0) }
    var duplicateRoutes: JsonElement = kotlinx.serialization.json.JsonArray(emptyList())
    var restartCount: Int = 0
        private set
    var shutdownCount: Int = 0
        private set
    val tasks: MutableMap<Int, TaskResource> = linkedMapOf()
    val updates: MutableList<UpdateResource> = mutableListOf()
    val updateLogFiles: MutableList<LogFileResource> = mutableListOf()
    var localization: LocalizationResource = LocalizationResource(
        strings = mapOf("Settings" to "Settings")
    )
    val autoTaggings: MutableMap<Int, AutoTaggingResource> = linkedMapOf()
    var autoTaggingSchema: JsonObject = buildJsonObject {
        put("implementationName", "Root Folder")
    }
    var hostConfig: HostConfigResource = HostConfigResource(id = 1)
    var mediaManagementConfig: MediaManagementConfigResource = MediaManagementConfigResource(
        id = 1
    )
    var namingConfig: NamingConfigResource = NamingConfigResource(id = 1)
    var namingExamples: JsonObject = buildJsonObject {
        put("standardTrackFormat", "{Artist Name} - {Album Title} - {Track Title}")
    }
    var uiConfig: UiConfigResource = UiConfigResource(id = 1)
    val metadatas: MutableMap<Int, MetadataResource> = linkedMapOf()
    val testedMetadatas: MutableList<MetadataResource> = mutableListOf()
    val metadataActions: MutableMap<String, MutableList<MetadataResource>> = linkedMapOf()
    var testAllMetadatasCount: Int = 0
        private set
    val notifications: MutableMap<Int, NotificationResource> = linkedMapOf()
    val testedNotifications: MutableList<NotificationResource> = mutableListOf()
    val notificationActions: MutableMap<String, MutableList<NotificationResource>> =
        linkedMapOf()
    var testAllNotificationsCount: Int = 0
        private set
    val downloadClients: MutableMap<Int, DownloadClientResource> = linkedMapOf()
    val testedDownloadClients: MutableList<DownloadClientResource> = mutableListOf()
    val downloadClientActions:
        MutableMap<String, MutableList<DownloadClientResource>> = linkedMapOf()
    var testAllDownloadClientsCount: Int = 0
        private set
    var downloadClientConfig: DownloadClientConfigResource = DownloadClientConfigResource(id = 1)
    val indexers: MutableMap<Int, IndexerResource> = linkedMapOf()
    val testedIndexers: MutableList<IndexerResource> = mutableListOf()
    val indexerActions: MutableMap<String, MutableList<IndexerResource>> = linkedMapOf()
    var testAllIndexersCount: Int = 0
        private set
    var indexerConfig: IndexerConfigResource = IndexerConfigResource(id = 1)
    val indexerFlags: MutableList<IndexerFlagResource> = mutableListOf()
    val remotePathMappings: MutableMap<Int, RemotePathMappingResource> = linkedMapOf()
    val rootFolders: MutableMap<Int, RootFolderResource> = linkedMapOf()
    val tags: MutableMap<Int, TagResource> = linkedMapOf()
    val tagDetails: MutableMap<Int, TagDetailsResource> = linkedMapOf()
    var fileSystem: JsonObject = buildJsonObject { put("directories", 0) }
    var fileSystemType: JsonElement = kotlinx.serialization.json.JsonPrimitive("folder")
    var mediaFiles: JsonElement = kotlinx.serialization.json.JsonArray(emptyList())

    private suspend fun before(operation: String) {
        latency(operation)
        error(operation)?.let { throw it }
    }

    private fun missingId(): Nothing = throw HttpException(
        Response.error<Unit>(404, "{}".toResponseBody("application/json".toMediaType()))
    )

    private fun nextId(ids: Set<Int>): Int = (ids.maxOrNull() ?: 0) + 1

    private fun intIdOr404(id: String): Int = id.toIntOrNull() ?: missingId()

    private fun textResponse(content: String): Response<ResponseBody> = Response.success(
        content.toResponseBody("text/plain".toMediaType())
    )

    private fun notFoundResponse(): Response<ResponseBody> = Response.error(
        404, "".toResponseBody("text/plain".toMediaType())
    )

    private fun fileResponse(contents: Map<String, String>, filename: String) =
        contents[filename]?.let { textResponse(it) } ?: notFoundResponse()

    private fun <T> page(items: List<T>, page: Int?, size: Int?): List<T> {
        val start = ((page ?: 1).coerceAtLeast(1).toLong() - 1) * (size ?: 10).coerceAtLeast(1)
        return items.drop(start.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            .take((size ?: 10).coerceAtLeast(1))
    }

    override suspend fun getApiInfo(): JsonObject {
        before("getApiInfo")
        return apiInfo
    }

    override suspend fun login(
        username: RequestBody,
        password: RequestBody,
        rememberMe: RequestBody?,
        returnUrl: String?,
    ) {
        before("login")
        loginCount++
    }

    override suspend fun logout() {
        before("logout")
        logoutCount++
    }

    override suspend fun getBackups(): List<BackupResource> {
        before("getBackups")
        return backups.values.toList()
    }

    override suspend fun deleteBackup(id: Int) {
        before("deleteBackup")
        backups.remove(id) ?: missingId()
    }

    override suspend fun restoreBackup(id: Int) {
        before("restoreBackup")
        if (id !in backups) missingId()
        restoredBackupIds += id
    }

    override suspend fun restoreUploadedBackup() {
        before("restoreUploadedBackup")
        restoreUploadCount++
    }

    override suspend fun getDiskSpace(): List<DiskSpaceResource> {
        before("getDiskSpace")
        return diskSpace.toList()
    }

    override suspend fun getHealth(): List<HealthResource> {
        before("getHealth")
        return health.toList()
    }

    override suspend fun getLog(
        page: Int?,
        pageSize: Int?,
        sortKey: String?,
        sortDirection: SortDirection?,
        level: String?,
    ): LogResourcePagingResource {
        before("getLog")
        val wanted = level?.let { wanted ->
            logRecords.filter { it.level?.equals(wanted, ignoreCase = true) == true }
        } ?: logRecords.toList()
        val ordered = if (sortKey == null || sortKey == "time") {
            wanted.sortedBy { it.time }
        } else {
            wanted
        }
        val matches = if (sortDirection == SortDirection.DESCENDING) ordered.reversed() else ordered
        return LogResourcePagingResource(
            page = page ?: 1,
            pageSize = pageSize ?: 10,
            sortKey = sortKey,
            sortDirection = sortDirection ?: SortDirection.DEFAULT,
            totalRecords = matches.size,
            records = page(matches, page, pageSize),
        )
    }

    override suspend fun getLogFiles(): List<LogFileResource> {
        before("getLogFiles")
        return logFiles.toList()
    }

    override suspend fun getLogFile(filename: String): Response<ResponseBody> {
        before("getLogFile")
        return fileResponse(logFileContents, filename)
    }

    override suspend fun getStatus(): SystemResource {
        before("getStatus")
        return systemStatus
    }

    override suspend fun getRoutes(): JsonObject {
        before("getRoutes")
        return routes
    }

    override suspend fun getDuplicateRoutes(): JsonElement {
        before("getDuplicateRoutes")
        return duplicateRoutes
    }

    override suspend fun restart() {
        before("restart")
        restartCount++
    }

    override suspend fun shutdown() {
        before("shutdown")
        shutdownCount++
    }

    override suspend fun getTasks(): List<TaskResource> {
        before("getTasks")
        return tasks.values.toList()
    }

    override suspend fun getTask(id: Int): TaskResource {
        before("getTask")
        return tasks[id] ?: missingId()
    }

    override suspend fun getUpdates(): List<UpdateResource> {
        before("getUpdates")
        return updates.toList()
    }

    override suspend fun getUpdateLogFiles(): List<LogFileResource> {
        before("getUpdateLogFiles")
        return updateLogFiles.toList()
    }

    override suspend fun getUpdateLogFile(filename: String): Response<ResponseBody> {
        before("getUpdateLogFile")
        return fileResponse(logFileContents, filename)
    }

    override suspend fun getLocalization(): LocalizationResource {
        before("getLocalization")
        return localization
    }

    override suspend fun getAutoTaggings(): List<AutoTaggingResource> {
        before("getAutoTaggings")
        return autoTaggings.values.toList()
    }

    override suspend fun getAutoTagging(id: Int): AutoTaggingResource {
        before("getAutoTagging")
        return autoTaggings[id] ?: missingId()
    }

    override suspend fun addAutoTagging(resource: AutoTaggingResource): AutoTaggingResource {
        before("addAutoTagging")
        val id = nextId(autoTaggings.keys)
        val created = resource.copy(id = id)
        autoTaggings[id] = created
        return created
    }

    override suspend fun updateAutoTagging(
        id: String,
        resource: AutoTaggingResource,
    ): AutoTaggingResource {
        before("updateAutoTagging")
        val intId = intIdOr404(id)
        if (intId !in autoTaggings) missingId()
        val updated = resource.copy(id = intId)
        autoTaggings[intId] = updated
        return updated
    }

    override suspend fun deleteAutoTagging(id: Int) {
        before("deleteAutoTagging")
        autoTaggings.remove(id) ?: missingId()
    }

    override suspend fun getAutoTaggingSchema(): JsonObject {
        before("getAutoTaggingSchema")
        return autoTaggingSchema
    }

    override suspend fun getHostConfig(): HostConfigResource {
        before("getHostConfig")
        return hostConfig
    }

    override suspend fun getHostConfigById(id: Int): HostConfigResource {
        before("getHostConfigById")
        return hostConfig
    }

    override suspend fun updateHostConfig(
        id: String,
        resource: HostConfigResource,
    ): HostConfigResource {
        before("updateHostConfig")
        val intId = intIdOr404(id)
        hostConfig = resource.copy(id = intId)
        return hostConfig
    }

    override suspend fun getMediaManagementConfig(): MediaManagementConfigResource {
        before("getMediaManagementConfig")
        return mediaManagementConfig
    }

    override suspend fun getMediaManagementConfigById(
        id: Int,
    ): MediaManagementConfigResource {
        before("getMediaManagementConfigById")
        return mediaManagementConfig
    }

    override suspend fun updateMediaManagementConfig(
        id: String,
        resource: MediaManagementConfigResource,
    ): MediaManagementConfigResource {
        before("updateMediaManagementConfig")
        val intId = intIdOr404(id)
        mediaManagementConfig = resource.copy(id = intId)
        return mediaManagementConfig
    }

    override suspend fun getNamingConfig(): NamingConfigResource {
        before("getNamingConfig")
        return namingConfig
    }

    override suspend fun getNamingConfigById(id: Int): NamingConfigResource {
        before("getNamingConfigById")
        return namingConfig
    }

    override suspend fun updateNamingConfig(
        id: String,
        resource: NamingConfigResource,
    ): NamingConfigResource {
        before("updateNamingConfig")
        val intId = intIdOr404(id)
        namingConfig = resource.copy(id = intId)
        return namingConfig
    }

    override suspend fun getNamingExamples(
        renameTracks: Boolean?,
        replaceIllegalCharacters: Boolean?,
        colonReplacementFormat: Int?,
        standardTrackFormat: String?,
        multiDiscTrackFormat: String?,
        artistFolderFormat: String?,
        includeArtistName: Boolean?,
        includeAlbumTitle: Boolean?,
        includeQuality: Boolean?,
        replaceSpaces: Boolean?,
        separator: String?,
        numberStyle: String?,
        id: Int?,
        resourceName: String?,
    ): JsonObject {
        before("getNamingExamples")
        return namingExamples
    }

    override suspend fun getUiConfig(): UiConfigResource {
        before("getUiConfig")
        return uiConfig
    }

    override suspend fun getUiConfigById(id: Int): UiConfigResource {
        before("getUiConfigById")
        return uiConfig
    }

    override suspend fun updateUiConfig(
        id: String,
        resource: UiConfigResource,
    ): UiConfigResource {
        before("updateUiConfig")
        val intId = intIdOr404(id)
        uiConfig = resource.copy(id = intId)
        return uiConfig
    }

    override suspend fun getMetadatas(): List<MetadataResource> {
        before("getMetadatas")
        return metadatas.values.toList()
    }

    override suspend fun getMetadata(id: Int): MetadataResource {
        before("getMetadata")
        return metadatas[id] ?: missingId()
    }

    override suspend fun addMetadata(
        forceSave: Boolean?,
        resource: MetadataResource,
    ): MetadataResource {
        before("addMetadata")
        val id = nextId(metadatas.keys)
        val created = resource.copy(id = id)
        metadatas[id] = created
        return created
    }

    override suspend fun updateMetadata(
        id: Int,
        forceSave: Boolean?,
        resource: MetadataResource,
    ): MetadataResource {
        before("updateMetadata")
        if (id !in metadatas) missingId()
        val updated = resource.copy(id = id)
        metadatas[id] = updated
        return updated
    }

    override suspend fun deleteMetadata(id: Int) {
        before("deleteMetadata")
        metadatas.remove(id) ?: missingId()
    }

    override suspend fun getMetadataSchema(): List<MetadataResource> {
        before("getMetadataSchema")
        // Schemas are provider templates, not stored state; presets stand in for them.
        return metadatas.values.filter { it.presets != null }
    }

    override suspend fun testMetadata(forceTest: Boolean?, resource: MetadataResource) {
        before("testMetadata")
        testedMetadatas += resource
    }

    override suspend fun testAllMetadata() {
        before("testAllMetadata")
        testAllMetadatasCount++
    }

    override suspend fun metadataAction(name: String, resource: MetadataResource) {
        before("metadataAction")
        metadataActions.getOrPut(name) { mutableListOf() } += resource
    }

    override suspend fun getNotifications(): List<NotificationResource> {
        before("getNotifications")
        return notifications.values.toList()
    }

    override suspend fun getNotification(id: Int): NotificationResource {
        before("getNotification")
        return notifications[id] ?: missingId()
    }

    override suspend fun addNotification(
        forceSave: Boolean?,
        resource: NotificationResource,
    ): NotificationResource {
        before("addNotification")
        val id = nextId(notifications.keys)
        val created = resource.copy(id = id)
        notifications[id] = created
        return created
    }

    override suspend fun updateNotification(
        id: Int,
        forceSave: Boolean?,
        resource: NotificationResource,
    ): NotificationResource {
        before("updateNotification")
        if (id !in notifications) missingId()
        val updated = resource.copy(id = id)
        notifications[id] = updated
        return updated
    }

    override suspend fun deleteNotification(id: Int) {
        before("deleteNotification")
        notifications.remove(id) ?: missingId()
    }

    override suspend fun getNotificationSchema(): List<NotificationResource> {
        before("getNotificationSchema")
        return notifications.values.filter { it.presets != null }
    }

    override suspend fun testNotification(forceTest: Boolean?, resource: NotificationResource) {
        before("testNotification")
        testedNotifications += resource
    }

    override suspend fun testAllNotifications() {
        before("testAllNotifications")
        testAllNotificationsCount++
    }

    override suspend fun notificationAction(name: String, resource: NotificationResource) {
        before("notificationAction")
        notificationActions.getOrPut(name) { mutableListOf() } += resource
    }

    override suspend fun getDownloadClients(): List<DownloadClientResource> {
        before("getDownloadClients")
        return downloadClients.values.toList()
    }

    override suspend fun getDownloadClient(id: Int): DownloadClientResource {
        before("getDownloadClient")
        return downloadClients[id] ?: missingId()
    }

    override suspend fun addDownloadClient(
        forceSave: Boolean?,
        resource: DownloadClientResource,
    ): DownloadClientResource {
        before("addDownloadClient")
        val id = nextId(downloadClients.keys)
        val created = resource.copy(id = id)
        downloadClients[id] = created
        return created
    }

    override suspend fun updateDownloadClient(
        id: Int,
        forceSave: Boolean?,
        resource: DownloadClientResource,
    ): DownloadClientResource {
        before("updateDownloadClient")
        if (id !in downloadClients) missingId()
        val updated = resource.copy(id = id)
        downloadClients[id] = updated
        return updated
    }

    override suspend fun deleteDownloadClient(id: Int) {
        before("deleteDownloadClient")
        downloadClients.remove(id) ?: missingId()
    }

    override suspend fun updateDownloadClients(
        resource: DownloadClientBulkResource,
    ): DownloadClientResource {
        before("updateDownloadClients")
        val targets = resource.ids.orEmpty().mapNotNull { downloadClients[it] }
        if (targets.isEmpty()) missingId()
        var updated: DownloadClientResource? = null
        for (target in targets) {
            val merged = target.copy(
                tags = when (resource.applyTags) {
                    ApplyTags.ADD -> (target.tags.orEmpty() + resource.tags.orEmpty()).toSortedSet()
                    ApplyTags.REMOVE -> (target.tags.orEmpty() - resource.tags.orEmpty().toSet())
                        .toSortedSet()

                    else -> resource.tags?.toSortedSet() ?: target.tags
                },
                enable = resource.enable ?: target.enable,
                priority = resource.priority ?: target.priority,
                removeCompletedDownloads =
                    resource.removeCompletedDownloads ?: target.removeCompletedDownloads,

                removeFailedDownloads = resource.removeFailedDownloads ?: target.removeFailedDownloads,
            )
            downloadClients[merged.id] = merged
            updated = merged
        }
        // The spec types the bulk response as one resource; return the last merged item.
        return updated ?: missingId()
    }

    override suspend fun deleteDownloadClients(resource: DownloadClientBulkResource) {
        before("deleteDownloadClients")
        resource.ids.orEmpty().forEach { downloadClients.remove(it) }
    }

    override suspend fun getDownloadClientSchema(): List<DownloadClientResource> {
        before("getDownloadClientSchema")
        return downloadClients.values.filter { it.presets != null }
    }

    override suspend fun testDownloadClient(
        forceTest: Boolean?,
        resource: DownloadClientResource,
    ) {
        before("testDownloadClient")
        testedDownloadClients += resource
    }

    override suspend fun testAllDownloadClients() {
        before("testAllDownloadClients")
        testAllDownloadClientsCount++
    }

    override suspend fun downloadClientAction(
        name: String,
        resource: DownloadClientResource,
    ) {
        before("downloadClientAction")
        downloadClientActions.getOrPut(name) { mutableListOf() } += resource
    }

    override suspend fun getDownloadClientConfig(): DownloadClientConfigResource {
        before("getDownloadClientConfig")
        return downloadClientConfig
    }

    override suspend fun getDownloadClientConfigById(id: Int): DownloadClientConfigResource {
        before("getDownloadClientConfigById")
        return downloadClientConfig
    }

    override suspend fun updateDownloadClientConfig(
        id: String,
        resource: DownloadClientConfigResource,
    ): DownloadClientConfigResource {
        before("updateDownloadClientConfig")
        val intId = intIdOr404(id)
        downloadClientConfig = resource.copy(id = intId)
        return downloadClientConfig
    }

    override suspend fun getIndexers(): List<IndexerResource> {
        before("getIndexers")
        return indexers.values.toList()
    }

    override suspend fun getIndexer(id: Int): IndexerResource {
        before("getIndexer")
        return indexers[id] ?: missingId()
    }

    override suspend fun addIndexer(
        forceSave: Boolean?,
        resource: IndexerResource,
    ): IndexerResource {
        before("addIndexer")
        val id = nextId(indexers.keys)
        val created = resource.copy(id = id)
        indexers[id] = created
        return created
    }

    override suspend fun updateIndexer(
        id: Int,
        forceSave: Boolean?,
        resource: IndexerResource,
    ): IndexerResource {
        before("updateIndexer")
        if (id !in indexers) missingId()
        val updated = resource.copy(id = id)
        indexers[id] = updated
        return updated
    }

    override suspend fun deleteIndexer(id: Int) {
        before("deleteIndexer")
        indexers.remove(id) ?: missingId()
    }

    override suspend fun updateIndexers(resource: IndexerBulkResource): IndexerResource {
        before("updateIndexers")
        val targets = resource.ids.orEmpty().mapNotNull { indexers[it] }
        if (targets.isEmpty()) missingId()
        var updated: IndexerResource? = null
        for (target in targets) {
            val merged = target.copy(
                tags = when (resource.applyTags) {
                    ApplyTags.ADD -> (target.tags.orEmpty() + resource.tags.orEmpty()).toSortedSet()
                    ApplyTags.REMOVE -> (target.tags.orEmpty() - resource.tags.orEmpty().toSet())
                        .toSortedSet()

                    else -> resource.tags?.toSortedSet() ?: target.tags
                },
                enableRss = resource.enableRss ?: target.enableRss,
                enableAutomaticSearch = resource.enableAutomaticSearch ?: target.enableAutomaticSearch,
                enableInteractiveSearch =
                    resource.enableInteractiveSearch ?: target.enableInteractiveSearch,

                priority = resource.priority ?: target.priority,
            )
            indexers[merged.id] = merged
            updated = merged
        }
        // The spec types the bulk response as one resource; return the last merged item.
        return updated ?: missingId()
    }

    override suspend fun deleteIndexers(resource: IndexerBulkResource) {
        before("deleteIndexers")
        resource.ids.orEmpty().forEach { indexers.remove(it) }
    }

    override suspend fun getIndexerSchema(): List<IndexerResource> {
        before("getIndexerSchema")
        return indexers.values.filter { it.presets != null }
    }

    override suspend fun testIndexer(forceTest: Boolean?, resource: IndexerResource) {
        before("testIndexer")
        testedIndexers += resource
    }

    override suspend fun testAllIndexers() {
        before("testAllIndexers")
        testAllIndexersCount++
    }

    override suspend fun indexerAction(name: String, resource: IndexerResource) {
        before("indexerAction")
        indexerActions.getOrPut(name) { mutableListOf() } += resource
    }

    override suspend fun getIndexerConfig(): IndexerConfigResource {
        before("getIndexerConfig")
        return indexerConfig
    }

    override suspend fun getIndexerConfigById(id: Int): IndexerConfigResource {
        before("getIndexerConfigById")
        return indexerConfig
    }

    override suspend fun updateIndexerConfig(
        id: String,
        resource: IndexerConfigResource,
    ): IndexerConfigResource {
        before("updateIndexerConfig")
        val intId = intIdOr404(id)
        indexerConfig = resource.copy(id = intId)
        return indexerConfig
    }

    override suspend fun getIndexerFlags(): List<IndexerFlagResource> {
        before("getIndexerFlags")
        return indexerFlags.toList()
    }

    override suspend fun getRemotePathMappings(): List<RemotePathMappingResource> {
        before("getRemotePathMappings")
        return remotePathMappings.values.toList()
    }

    override suspend fun getRemotePathMapping(id: Int): RemotePathMappingResource {
        before("getRemotePathMapping")
        return remotePathMappings[id] ?: missingId()
    }

    override suspend fun addRemotePathMapping(
        resource: RemotePathMappingResource,
    ): RemotePathMappingResource {
        before("addRemotePathMapping")
        val id = nextId(remotePathMappings.keys)
        val created = resource.copy(id = id)
        remotePathMappings[id] = created
        return created
    }

    override suspend fun updateRemotePathMapping(
        id: String,
        resource: RemotePathMappingResource,
    ): RemotePathMappingResource {
        before("updateRemotePathMapping")
        val intId = intIdOr404(id)
        if (intId !in remotePathMappings) missingId()
        val updated = resource.copy(id = intId)
        remotePathMappings[intId] = updated
        return updated
    }

    override suspend fun deleteRemotePathMapping(id: Int) {
        before("deleteRemotePathMapping")
        remotePathMappings.remove(id) ?: missingId()
    }

    override suspend fun getRootFolders(): List<RootFolderResource> {
        before("getRootFolders")
        return rootFolders.values.toList()
    }

    override suspend fun getRootFolder(id: Int): RootFolderResource {
        before("getRootFolder")
        return rootFolders[id] ?: missingId()
    }

    override suspend fun addRootFolder(
        resource: RootFolderResource,
    ): RootFolderResource {
        before("addRootFolder")
        val id = nextId(rootFolders.keys)
        val created = resource.copy(id = id)
        rootFolders[id] = created
        return created
    }

    override suspend fun updateRootFolder(
        id: String,
        resource: RootFolderResource,
    ): RootFolderResource {
        before("updateRootFolder")
        val intId = intIdOr404(id)
        if (intId !in rootFolders) missingId()
        val updated = resource.copy(id = intId)
        rootFolders[intId] = updated
        return updated
    }

    override suspend fun deleteRootFolder(id: Int) {
        before("deleteRootFolder")
        rootFolders.remove(id) ?: missingId()
    }

    override suspend fun getTags(): List<TagResource> {
        before("getTags")
        return tags.values.toList()
    }

    override suspend fun getTag(id: Int): TagResource {
        before("getTag")
        return tags[id] ?: missingId()
    }

    override suspend fun addTag(resource: TagResource): TagResource {
        before("addTag")
        val id = nextId(tags.keys)
        val created = resource.copy(id = id)
        tags[id] = created
        return created
    }

    override suspend fun updateTag(id: String, resource: TagResource): TagResource {
        before("updateTag")
        val intId = intIdOr404(id)
        if (intId !in tags) missingId()
        val updated = resource.copy(id = intId)
        tags[intId] = updated
        return updated
    }

    override suspend fun deleteTag(id: Int) {
        before("deleteTag")
        tags.remove(id) ?: missingId()
    }

    override suspend fun getTagDetails(): List<TagDetailsResource> {
        before("getTagDetails")
        return tagDetails.values.toList()
    }

    override suspend fun getTagDetailsById(id: Int): TagDetailsResource {
        before("getTagDetailsById")
        return tagDetails[id] ?: missingId()
    }

    override suspend fun getFileSystem(
        path: String?,
        includeFiles: Boolean?,
        allowFoldersWithoutTrailingSlashes: Boolean?,
    ): JsonObject {
        before("getFileSystem")
        return fileSystem
    }

    override suspend fun getFileSystemType(path: String?): JsonElement {
        before("getFileSystemType")
        return fileSystemType
    }

    override suspend fun getMediaFiles(path: String?): JsonElement {
        before("getMediaFiles")
        return mediaFiles
    }
}
