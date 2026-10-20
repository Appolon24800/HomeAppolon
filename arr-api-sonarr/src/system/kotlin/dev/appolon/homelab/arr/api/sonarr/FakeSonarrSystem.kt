package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.ApiInfoResource
import dev.appolon.homelab.arr.api.sonarr.model.AutoTaggingResource
import dev.appolon.homelab.arr.api.sonarr.model.BackupResource
import dev.appolon.homelab.arr.api.sonarr.model.DiskSpaceResource
import dev.appolon.homelab.arr.api.sonarr.model.HealthResource
import dev.appolon.homelab.arr.api.sonarr.model.HostConfigResource
import dev.appolon.homelab.arr.api.sonarr.model.LocalizationLanguageResource
import dev.appolon.homelab.arr.api.sonarr.model.LocalizationResource
import dev.appolon.homelab.arr.api.sonarr.model.LogFileResource
import dev.appolon.homelab.arr.api.sonarr.model.LogResourcePagingResource
import dev.appolon.homelab.arr.api.sonarr.model.MediaManagementConfigResource
import dev.appolon.homelab.arr.api.sonarr.model.MetadataResource
import dev.appolon.homelab.arr.api.sonarr.model.NamingConfigResource
import dev.appolon.homelab.arr.api.sonarr.model.RootFolderResource
import dev.appolon.homelab.arr.api.sonarr.model.SortDirection
import dev.appolon.homelab.arr.api.sonarr.model.SystemResource
import dev.appolon.homelab.arr.api.sonarr.model.TagDetailsResource
import dev.appolon.homelab.arr.api.sonarr.model.TagResource
import dev.appolon.homelab.arr.api.sonarr.model.TaskResource
import dev.appolon.homelab.arr.api.sonarr.model.UiConfigResource
import dev.appolon.homelab.arr.api.sonarr.model.UpdateResource
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MultipartBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

/**
 * Mutable, single-test state for every system- and settings-tag interface.
 * Supply [latency] to delay calls and [error] to inject failures. The
 * callbacks receive the OpenAPI tag and method name, such as `Tag.list`.
 * The fake never touches the file system, restarts anything, or contacts
 * metadata providers.
 */
class FakeSonarrSystem(
    var latency: suspend (String) -> Unit = {},
    var error: (String) -> Throwable? = { null }
) {
    var apiInfo: ApiInfoResource = ApiInfoResource(current = "v3")
    val authenticationCalls: MutableList<String> = mutableListOf()
    var loginResponse: Response<Unit> = Response.success(Unit)

    val autoTaggingStore: MutableMap<Int, AutoTaggingResource> = linkedMapOf()
    var autoTaggingSchema: JsonObject = buildJsonObject { }

    val backups: MutableList<BackupResource> = mutableListOf()
    val restoredBackupIds: MutableList<Int> = mutableListOf()
    var restoreUploadCount: Int = 0
        private set

    val diskSpace: MutableList<DiskSpaceResource> = mutableListOf()

    var fileSystemListing: JsonObject = buildJsonObject { }
    var fileSystemType: JsonObject = buildJsonObject { }
    var fileSystemMediaFiles: JsonObject = buildJsonObject { }

    val health: MutableList<HealthResource> = mutableListOf()

    var hostConfig: HostConfigResource = HostConfigResource()

    var localization: LocalizationResource = LocalizationResource()
    var localizationLanguage: LocalizationLanguageResource = LocalizationLanguageResource()

    var logPage: LogResourcePagingResource = LogResourcePagingResource()
    val logFiles: MutableList<LogFileResource> = mutableListOf()
    var logFileContents: JsonObject = buildJsonObject { }

    var mediaManagementConfig: MediaManagementConfigResource = MediaManagementConfigResource()

    val metadataStore: MutableMap<Int, MetadataResource> = linkedMapOf()
    val metadataSchemas: MutableList<MetadataResource> = mutableListOf()
    val testedMetadata: MutableList<MetadataResource> = mutableListOf()
    var metadataTestAllCount: Int = 0
        private set
    val metadataActionCalls: MutableMap<String, MutableList<MetadataResource>> = linkedMapOf()

    var namingConfig: NamingConfigResource = NamingConfigResource()
    val namingExampleRequests: MutableList<Map<String, String?>> = mutableListOf()

    val rootFolderStore: MutableMap<Int, RootFolderResource> = linkedMapOf()

    var systemStatus: SystemResource = SystemResource()
    var systemRoutes: JsonObject = buildJsonObject { }
    var systemDuplicateRoutes: JsonObject = buildJsonObject { }
    var shutdownCount: Int = 0
        private set
    var restartCount: Int = 0
        private set

    val tagStore: MutableMap<Int, TagResource> = linkedMapOf()
    val tagDetailsStore: MutableMap<Int, TagDetailsResource> = linkedMapOf()
    val taskStore: MutableMap<Int, TaskResource> = linkedMapOf()
    var uiConfig: UiConfigResource = UiConfigResource()
    val updates: MutableList<UpdateResource> = mutableListOf()
    val updateLogFiles: MutableList<LogFileResource> = mutableListOf()
    var updateLogFileContents: JsonObject = buildJsonObject { }

    var forcedSaves: Int = 0
        private set
    var forcedTests: Int = 0
        private set

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

    val apiInfoApi: ApiInfoApi = object : ApiInfoApi {
        override suspend fun get(): ApiInfoResource {
            before("ApiInfo.get")
            return apiInfo
        }
    }

    val authenticationApi: AuthenticationApi = object : AuthenticationApi {
        override suspend fun sso(returnUrl: String?): Response<Unit> {
            before("Authentication.sso")
            authenticationCalls.add("sso:$returnUrl")
            return Response.success(Unit)
        }

        override suspend fun login(returnUrl: String?, body: MultipartBody): Response<Unit> {
            before("Authentication.login")
            authenticationCalls.add("login:$returnUrl")
            return loginResponse
        }

        override suspend fun requestPasswordReset(): Response<Unit> {
            before("Authentication.requestPasswordReset")
            authenticationCalls.add("requestPasswordReset")
            return Response.success(Unit)
        }

        override suspend fun resetPassword(body: MultipartBody): Response<Unit> {
            before("Authentication.resetPassword")
            authenticationCalls.add("resetPassword")
            return Response.success(Unit)
        }

        override suspend fun logout(): Response<Unit> {
            before("Authentication.logout")
            authenticationCalls.add("logout")
            return Response.success(Unit)
        }
    }

    val autoTaggingApi: AutoTaggingApi = object : AutoTaggingApi {
        override suspend fun create(body: AutoTaggingResource): AutoTaggingResource {
            before("AutoTagging.create")
            val id = if (body.id == 0) nextId(autoTaggingStore) else body.id
            return body.copy(id = id).also { autoTaggingStore[id] = it }
        }

        override suspend fun list(): List<AutoTaggingResource> {
            before("AutoTagging.list")
            return autoTaggingStore.values.toList()
        }

        override suspend fun update(
            id: String,
            body: AutoTaggingResource
        ): AutoTaggingResource {
            before("AutoTagging.update")
            val key = id.toIntOrNull() ?: missing(0)
            val current = autoTaggingStore[key] ?: missing(key)
            return body.copy(id = current.id).also { autoTaggingStore[current.id] = it }
        }

        override suspend fun delete(id: Int): Unit {
            before("AutoTagging.delete")
            autoTaggingStore.remove(id) ?: missing(id)
            return Unit
        }

        override suspend fun get(id: Int): Response<AutoTaggingResource> {
            before("AutoTagging.get")
            val current = autoTaggingStore[id] ?: return notFound(id)
            return Response.success(current)
        }

        override suspend fun schema(): JsonObject {
            before("AutoTagging.schema")
            return autoTaggingSchema
        }
    }

    val backupApi: BackupApi = object : BackupApi {
        override suspend fun list(): List<BackupResource> {
            before("Backup.list")
            return backups.toList()
        }

        override suspend fun delete(id: Int): Unit {
            before("Backup.delete")
            backups.removeIf { it.id == id }
            return Unit
        }

        override suspend fun restore(id: Int): Unit {
            before("Backup.restore")
            restoredBackupIds.add(id)
            return Unit
        }

        override suspend fun restoreUpload(): Unit {
            before("Backup.restoreUpload")
            restoreUploadCount++
            return Unit
        }
    }

    val diskSpaceApi: DiskSpaceApi = object : DiskSpaceApi {
        override suspend fun list(): List<DiskSpaceResource> {
            before("DiskSpace.list")
            return diskSpace.toList()
        }
    }

    val fileSystemApi: FileSystemApi = object : FileSystemApi {
        override suspend fun list(
            path: String?,
            includeFiles: Boolean?,
            allowFoldersWithoutTrailingSlashes: Boolean?
        ): JsonObject {
            before("FileSystem.list")
            return fileSystemListing
        }

        override suspend fun type(path: String?): JsonObject {
            before("FileSystem.type")
            return fileSystemType
        }

        override suspend fun mediaFiles(path: String?): JsonObject {
            before("FileSystem.mediaFiles")
            return fileSystemMediaFiles
        }
    }

    val healthApi: HealthApi = object : HealthApi {
        override suspend fun list(): List<HealthResource> {
            before("Health.list")
            return health.toList()
        }
    }

    val hostConfigApi: HostConfigApi = object : HostConfigApi {
        override suspend fun get(): HostConfigResource {
            before("HostConfig.get")
            return hostConfig
        }

        override suspend fun update(
            id: String,
            body: HostConfigResource
        ): HostConfigResource {
            before("HostConfig.update")
            hostConfig = body.copy(id = id.toIntOrNull() ?: body.id)
            return hostConfig
        }

        override suspend fun get(id: Int): Response<HostConfigResource> {
            before("HostConfig.get")
            if (hostConfig.id != id) return notFound(id)
            return Response.success(hostConfig)
        }
    }

    val localizationApi: LocalizationApi = object : LocalizationApi {
        override suspend fun get(): LocalizationResource {
            before("Localization.get")
            return localization
        }

        override suspend fun language(): LocalizationLanguageResource {
            before("Localization.language")
            return localizationLanguage
        }

        override suspend fun get(id: Int): Response<LocalizationResource> {
            before("Localization.get")
            if (localization.id != id) return notFound(id)
            return Response.success(localization)
        }
    }

    val logApi: LogApi = object : LogApi {
        override suspend fun list(
            page: Int?,
            pageSize: Int?,
            sortKey: String?,
            sortDirection: SortDirection?,
            level: String?
        ): LogResourcePagingResource {
            before("Log.list")
            return logPage
        }
    }

    val logFileApi: LogFileApi = object : LogFileApi {
        override suspend fun list(): List<LogFileResource> {
            before("LogFile.list")
            return logFiles.toList()
        }

        override suspend fun get(filename: String): JsonObject {
            before("LogFile.get")
            return logFileContents
        }
    }

    val mediaManagementConfigApi: MediaManagementConfigApi =
        object : MediaManagementConfigApi {
            override suspend fun get(): MediaManagementConfigResource {
                before("MediaManagementConfig.get")
                return mediaManagementConfig
            }

            override suspend fun update(
                id: String,
                body: MediaManagementConfigResource
            ): MediaManagementConfigResource {
                before("MediaManagementConfig.update")
                mediaManagementConfig = body.copy(id = id.toIntOrNull() ?: body.id)
                return mediaManagementConfig
            }

            override suspend fun get(id: Int): Response<MediaManagementConfigResource> {
                before("MediaManagementConfig.get")
                if (mediaManagementConfig.id != id) return notFound(id)
                return Response.success(mediaManagementConfig)
            }
        }

    val metadataApi: MetadataApi = object : MetadataApi {
        override suspend fun list(): List<MetadataResource> {
            before("Metadata.list")
            return metadataStore.values.toList()
        }

        override suspend fun create(
            forceSave: Boolean?,
            body: MetadataResource
        ): MetadataResource {
            before("Metadata.create")
            if (forceSave == true) forcedSaves++
            val id = if (body.id == 0) nextId(metadataStore) else body.id
            return body.copy(id = id).also { metadataStore[id] = it }
        }

        override suspend fun update(
            id: Int,
            forceSave: Boolean?,
            body: MetadataResource
        ): MetadataResource {
            before("Metadata.update")
            if (forceSave == true) forcedSaves++
            val current = metadataStore[id] ?: missing(id)
            return body.copy(id = current.id).also { metadataStore[current.id] = it }
        }

        override suspend fun delete(id: Int): Unit {
            before("Metadata.delete")
            metadataStore.remove(id) ?: missing(id)
            return Unit
        }

        override suspend fun get(id: Int): Response<MetadataResource> {
            before("Metadata.get")
            val current = metadataStore[id] ?: return notFound(id)
            return Response.success(current)
        }

        override suspend fun schema(): List<MetadataResource> {
            before("Metadata.schema")
            return metadataSchemas.toList()
        }

        override suspend fun test(
            forceTest: Boolean?,
            body: MetadataResource
        ): Unit {
            before("Metadata.test")
            if (forceTest == true) forcedTests++
            testedMetadata.add(body)
            return Unit
        }

        override suspend fun testAll(): Unit {
            before("Metadata.testAll")
            metadataTestAllCount++
            return Unit
        }

        override suspend fun action(
            name: String,
            body: MetadataResource
        ): Unit {
            before("Metadata.action")
            metadataActionCalls.getOrPut(name) { mutableListOf() }.add(body)
            return Unit
        }
    }

    val namingConfigApi: NamingConfigApi = object : NamingConfigApi {
        override suspend fun get(): NamingConfigResource {
            before("NamingConfig.get")
            return namingConfig
        }

        override suspend fun update(
            id: String,
            body: NamingConfigResource
        ): NamingConfigResource {
            before("NamingConfig.update")
            namingConfig = body.copy(id = id.toIntOrNull() ?: body.id)
            return namingConfig
        }

        override suspend fun get(id: Int): Response<NamingConfigResource> {
            before("NamingConfig.get")
            if (namingConfig.id != id) return notFound(id)
            return Response.success(namingConfig)
        }

        override suspend fun examples(
            renameEpisodes: Boolean?,
            replaceIllegalCharacters: Boolean?,
            colonReplacementFormat: Int?,
            customColonReplacementFormat: String?,
            multiEpisodeStyle: Int?,
            standardEpisodeFormat: String?,
            dailyEpisodeFormat: String?,
            animeEpisodeFormat: String?,
            seriesFolderFormat: String?,
            seasonFolderFormat: String?,
            specialsFolderFormat: String?,
            id: Int?,
            resourceName: String?
        ): JsonObject {
            before("NamingConfig.examples")
            namingExampleRequests.add(
                mapOf(
                    "renameEpisodes" to renameEpisodes?.toString(),
                    "standardEpisodeFormat" to standardEpisodeFormat,
                    "resourceName" to resourceName
                )
            )
            return buildJsonObject { }
        }
    }

    val rootFolderApi: RootFolderApi = object : RootFolderApi {
        override suspend fun create(body: RootFolderResource): RootFolderResource {
            before("RootFolder.create")
            val id = if (body.id == 0) nextId(rootFolderStore) else body.id
            return body.copy(id = id).also { rootFolderStore[id] = it }
        }

        override suspend fun list(): List<RootFolderResource> {
            before("RootFolder.list")
            return rootFolderStore.values.toList()
        }

        override suspend fun delete(id: Int): Unit {
            before("RootFolder.delete")
            rootFolderStore.remove(id) ?: missing(id)
            return Unit
        }

        override suspend fun get(id: Int): Response<RootFolderResource> {
            before("RootFolder.get")
            val current = rootFolderStore[id] ?: return notFound(id)
            return Response.success(current)
        }
    }

    val systemApi: SystemApi = object : SystemApi {
        override suspend fun status(): SystemResource {
            before("System.status")
            return systemStatus
        }

        override suspend fun routes(): JsonObject {
            before("System.routes")
            return systemRoutes
        }

        override suspend fun routesDuplicate(): JsonObject {
            before("System.routesDuplicate")
            return systemDuplicateRoutes
        }

        override suspend fun shutdown(): Unit {
            before("System.shutdown")
            shutdownCount++
            return Unit
        }

        override suspend fun restart(): Unit {
            before("System.restart")
            restartCount++
            return Unit
        }
    }

    val tagApi: TagApi = object : TagApi {
        override suspend fun list(): List<TagResource> {
            before("Tag.list")
            return tagStore.values.toList()
        }

        override suspend fun create(body: TagResource): TagResource {
            before("Tag.create")
            val id = if (body.id == 0) nextId(tagStore) else body.id
            return body.copy(id = id).also { tagStore[id] = it }
        }

        override suspend fun update(
            id: String,
            body: TagResource
        ): TagResource {
            before("Tag.update")
            val key = id.toIntOrNull() ?: missing(0)
            val current = tagStore[key] ?: missing(key)
            return body.copy(id = current.id).also { tagStore[current.id] = it }
        }

        override suspend fun delete(id: Int): Unit {
            before("Tag.delete")
            tagStore.remove(id) ?: missing(id)
            return Unit
        }

        override suspend fun get(id: Int): Response<TagResource> {
            before("Tag.get")
            val current = tagStore[id] ?: return notFound(id)
            return Response.success(current)
        }
    }

    val tagDetailsApi: TagDetailsApi = object : TagDetailsApi {
        override suspend fun list(): List<TagDetailsResource> {
            before("TagDetails.list")
            return tagDetailsStore.values.toList()
        }

        override suspend fun get(id: Int): Response<TagDetailsResource> {
            before("TagDetails.get")
            val current = tagDetailsStore[id] ?: return notFound(id)
            return Response.success(current)
        }
    }

    val taskApi: TaskApi = object : TaskApi {
        override suspend fun list(): List<TaskResource> {
            before("Task.list")
            return taskStore.values.toList()
        }

        override suspend fun get(id: Int): Response<TaskResource> {
            before("Task.get")
            val current = taskStore[id] ?: return notFound(id)
            return Response.success(current)
        }
    }

    val uiConfigApi: UiConfigApi = object : UiConfigApi {
        override suspend fun update(
            id: String,
            body: UiConfigResource
        ): UiConfigResource {
            before("UiConfig.update")
            uiConfig = body.copy(id = id.toIntOrNull() ?: body.id)
            return uiConfig
        }

        override suspend fun get(id: Int): Response<UiConfigResource> {
            before("UiConfig.get")
            if (uiConfig.id != id) return notFound(id)
            return Response.success(uiConfig)
        }

        override suspend fun get(): UiConfigResource {
            before("UiConfig.get")
            return uiConfig
        }
    }

    val updateApi: UpdateApi = object : UpdateApi {
        override suspend fun list(): List<UpdateResource> {
            before("Update.list")
            return updates.toList()
        }
    }

    val updateLogFileApi: UpdateLogFileApi = object : UpdateLogFileApi {
        override suspend fun list(): List<LogFileResource> {
            before("UpdateLogFile.list")
            return updateLogFiles.toList()
        }

        override suspend fun get(filename: String): JsonObject {
            before("UpdateLogFile.get")
            return updateLogFileContents
        }
    }
}
