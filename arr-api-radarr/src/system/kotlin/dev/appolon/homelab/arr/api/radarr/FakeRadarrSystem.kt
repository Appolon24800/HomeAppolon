package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.ApiInfoResource
import dev.appolon.homelab.arr.api.radarr.model.AutoTaggingResource
import dev.appolon.homelab.arr.api.radarr.model.AutoTaggingSpecificationSchema
import dev.appolon.homelab.arr.api.radarr.model.BackupResource
import dev.appolon.homelab.arr.api.radarr.model.BackupRestoreResource
import dev.appolon.homelab.arr.api.radarr.model.ColonReplacementFormat
import dev.appolon.homelab.arr.api.radarr.model.DiskSpaceResource
import dev.appolon.homelab.arr.api.radarr.model.FileSystemMediaFileResource
import dev.appolon.homelab.arr.api.radarr.model.FileSystemResult
import dev.appolon.homelab.arr.api.radarr.model.FileSystemTypeResource
import dev.appolon.homelab.arr.api.radarr.model.HealthResource
import dev.appolon.homelab.arr.api.radarr.model.HostConfigResource
import dev.appolon.homelab.arr.api.radarr.model.LocalizationLanguageResource
import dev.appolon.homelab.arr.api.radarr.model.LogFileResource
import dev.appolon.homelab.arr.api.radarr.model.LogResource
import dev.appolon.homelab.arr.api.radarr.model.LogResourcePagingResource
import dev.appolon.homelab.arr.api.radarr.model.MediaManagementConfigResource
import dev.appolon.homelab.arr.api.radarr.model.MetadataConfigResource
import dev.appolon.homelab.arr.api.radarr.model.MetadataResource
import dev.appolon.homelab.arr.api.radarr.model.NamingConfigResource
import dev.appolon.homelab.arr.api.radarr.model.NamingExampleResource
import dev.appolon.homelab.arr.api.radarr.model.ParseResource
import dev.appolon.homelab.arr.api.radarr.model.PingResource
import dev.appolon.homelab.arr.api.radarr.model.ProviderTestAllResult
import dev.appolon.homelab.arr.api.radarr.model.RootFolderResource
import dev.appolon.homelab.arr.api.radarr.model.SortDirection
import dev.appolon.homelab.arr.api.radarr.model.SystemRestartResource
import dev.appolon.homelab.arr.api.radarr.model.SystemResource
import dev.appolon.homelab.arr.api.radarr.model.SystemShutdownResource
import dev.appolon.homelab.arr.api.radarr.model.TagDetailsResource
import dev.appolon.homelab.arr.api.radarr.model.TagResource
import dev.appolon.homelab.arr.api.radarr.model.TaskResource
import dev.appolon.homelab.arr.api.radarr.model.UiConfigResource
import dev.appolon.homelab.arr.api.radarr.model.UpdateResource
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.time.Instant

/**
 * Mutable single-test state implementing every system- and settings-group
 * interface. Inject a suspending [sleep] such as coroutine delay to use virtual
 * time without a runtime coroutine dependency. The default sleep blocks the
 * calling thread, so UI tests should inject it. No real filesystem, process
 * control, or network is touched: shutdown, restart, restore, and log reads
 * only flip flags or return configured values. [errorFor] receives the
 * operation name and runs before any state mutation.
 */
class FakeRadarrSystem(
    var latencyMs: Long = 0,
    var errorFor: (String) -> Throwable? = { null },
    private val sleep: suspend (Long) -> Unit = { Thread.sleep(it) },
    private val now: () -> String = { Instant.now().toString() },
) : ApiInfoApi, AuthenticationApi, AutoTaggingApi, BackupApi, DiskSpaceApi,
    FileSystemApi, HealthApi, HostConfigApi, LocalizationApi, LogApi, LogFileApi,
    MediaManagementConfigApi, MetadataApi, MetadataConfigApi, NamingConfigApi,
    ParseApi, PingApi, RootFolderApi, SystemApi, TagApi, TagDetailsApi, TaskApi,
    UiConfigApi, UpdateApi, UpdateLogFileApi {

    var apiInfoResult = ApiInfoResource(current = "v3", deprecated = emptyList())
    val backups = mutableListOf<BackupResource>()
    var restoreResult = BackupRestoreResource(restartRequired = true)
    val diskSpaceItems = mutableListOf<DiskSpaceResource>()
    var fileSystemResult = FileSystemResult()
    var fileSystemTypeResult = FileSystemTypeResource()
    val mediaFiles = mutableListOf<FileSystemMediaFileResource>()
    val healthItems = mutableListOf<HealthResource>()
    var hostConfigState = HostConfigResource(id = 1)
    var mediaManagementConfigState = MediaManagementConfigResource(id = 1)
    var metadataConfigState = MetadataConfigResource(id = 1)
    var uiConfigState = UiConfigResource(id = 1)
    var namingConfigState = NamingConfigResource(id = 1)
    var namingExamplesResult = NamingExampleResource()
    val metadataProviders = mutableListOf<MetadataResource>()
    var metadataTestResults: List<ProviderTestAllResult> = emptyList()
    var localizationJson: JsonObject = JsonObject(emptyMap())
    var localizationLanguageResult = LocalizationLanguageResource(identifier = "en")
    val logItems = mutableListOf<LogResource>()
    val logFileItems = mutableListOf<LogFileResource>()
    val updateLogFileItems = mutableListOf<LogFileResource>()
    var logFileContentsText = ""
    var updateLogFileContentsText = ""
    var parseResultsByTitle: Map<String, ParseResource> = emptyMap()
    var pingResult = PingResource(status = "ok")
    val rootFolderItems = mutableListOf<RootFolderResource>()
    var systemStatusResult = SystemResource(appName = "Radarr")
    var systemRoutesText = ""
    var systemRoutesDuplicateResult: Map<String, List<String>> = emptyMap()
    val tagItems = mutableListOf<TagResource>()
    var tagUsage: Map<Int, TagDetailsResource> = emptyMap()
    val taskItems = mutableListOf<TaskResource>()
    val updateItems = mutableListOf<UpdateResource>()
    val autoTaggingItems = mutableListOf<AutoTaggingResource>()
    var autoTaggingSchemaResult: List<AutoTaggingSpecificationSchema> = emptyList()

    /** Forms-auth acceptance pair; `null` accepts any credentials. */
    var credentials: Pair<String, String>? = null

    val loginAttempts = mutableListOf<LoginAttempt>()
    var loggedIn = false

    /** Side effects the real server would perform, recorded for assertions. */
    var restoreRequested = false
    var shutdownRequested = false
    var restartRequested = false

    data class LoginAttempt(
        val username: String,
        val password: String,
        val rememberMe: String?,
        val returnUrl: String?,
    )

    private suspend fun before(operation: String) {
        require(latencyMs >= 0) { "Latency must be nonnegative" }
        if (latencyMs > 0) sleep(latencyMs)
        errorFor(operation)?.let { throw it }
    }

    private fun <T> notFound(): Response<T> =
        Response.error(404, "{}".toResponseBody())

    private fun text(value: String): ResponseBody =
        value.toResponseBody("text/plain".toMediaType())

    private fun RequestBody.asText(): String {
        val buffer = okio.Buffer()
        writeTo(buffer)
        return buffer.readUtf8()
    }

    override suspend fun apiInfo(): ApiInfoResource {
        before("apiInfo")
        return apiInfoResult
    }

    override suspend fun loginSso(returnUrl: String?): Response<Unit> {
        before("loginSso")
        loggedIn = true
        return Response.success(Unit)
    }

    override suspend fun login(
        returnUrl: String?,
        username: RequestBody,
        password: RequestBody,
        rememberMe: RequestBody?,
    ): Response<Unit> {
        before("login")
        val attempt = LoginAttempt(
            username.asText(),
            password.asText(),
            rememberMe?.asText(),
            returnUrl,
        )
        loginAttempts += attempt
        val expected = credentials
        if (expected != null && attempt.username to attempt.password != expected) {
            return Response.error(401, "{}".toResponseBody())
        }
        loggedIn = true
        return Response.success(Unit)
    }

    override suspend fun logout(): Response<Unit> {
        before("logout")
        loggedIn = false
        return Response.success(Unit)
    }

    override suspend fun createAutoTagging(body: AutoTaggingResource): Response<AutoTaggingResource> {
        before("createAutoTagging")
        val created = body.copy(id = (autoTaggingItems.maxOfOrNull { it.id } ?: 0) + 1)
        autoTaggingItems += created
        return Response.success(201, created)
    }

    override suspend fun autoTaggings(): List<AutoTaggingResource> {
        before("autoTaggings")
        return autoTaggingItems.toList()
    }

    override suspend fun updateAutoTagging(
        id: String,
        body: AutoTaggingResource,
    ): Response<AutoTaggingResource> {
        before("updateAutoTagging")
        val numericId = id.toIntOrNull() ?: return notFound()
        val index = autoTaggingItems.indexOfFirst { it.id == numericId }
        if (index < 0) return notFound()
        autoTaggingItems[index] = body.copy(id = numericId)
        return Response.success(202, autoTaggingItems[index])
    }

    override suspend fun removeAutoTagging(id: Int): Response<Unit> {
        before("removeAutoTagging")
        if (autoTaggingItems.none { it.id == id }) return notFound()
        autoTaggingItems.removeAll { it.id == id }
        return Response.success(Unit)
    }

    override suspend fun autoTagging(id: Int): Response<AutoTaggingResource> {
        before("autoTagging")
        return autoTaggingItems.firstOrNull { it.id == id }
            ?.let { Response.success(it) } ?: notFound()
    }

    override suspend fun autoTaggingSchema(): List<AutoTaggingSpecificationSchema> {
        before("autoTaggingSchema")
        return autoTaggingSchemaResult.toList()
    }

    override suspend fun backups(): List<BackupResource> {
        before("backups")
        return backups.toList()
    }

    override suspend fun removeBackup(id: Int): Response<Unit> {
        before("removeBackup")
        if (backups.none { it.id == id }) return notFound()
        backups.removeAll { it.id == id }
        return Response.success(Unit)
    }

    override suspend fun restoreBackup(id: Int): Response<BackupRestoreResource> {
        before("restoreBackup")
        if (backups.none { it.id == id }) return notFound()
        restoreRequested = true
        return Response.success(restoreResult)
    }

    override suspend fun restoreBackupUpload(): Response<BackupRestoreResource> {
        before("restoreBackupUpload")
        restoreRequested = true
        return Response.success(restoreResult)
    }

    override suspend fun diskSpace(): List<DiskSpaceResource> {
        before("diskSpace")
        return diskSpaceItems.toList()
    }

    override suspend fun fileSystem(
        path: String?,
        includeFiles: Boolean?,
        allowFoldersWithoutTrailingSlashes: Boolean?,
    ): FileSystemResult {
        before("fileSystem")
        val directories = fileSystemResult.directories?.filter {
            path == null || it.path == path || it.path == "$path/"
        }
        val files = if (includeFiles == true) {
            fileSystemResult.files?.filter { path == null || it.path == path }
        } else {
            null
        }
        return fileSystemResult.copy(directories = directories, files = files)
    }

    override suspend fun fileSystemType(path: String?): FileSystemTypeResource {
        before("fileSystemType")
        return fileSystemTypeResult
    }

    override suspend fun fileSystemMediaFiles(path: String?): List<FileSystemMediaFileResource> {
        before("fileSystemMediaFiles")
        return mediaFiles.filter { path == null || it.path == path }.toList()
    }

    override suspend fun health(): List<HealthResource> {
        before("health")
        return healthItems.toList()
    }

    override suspend fun hostConfig(): HostConfigResource {
        before("hostConfig")
        return hostConfigState
    }

    override suspend fun updateHostConfig(id: String, body: HostConfigResource): Response<HostConfigResource> {
        before("updateHostConfig")
        hostConfigState = body.copy(id = id.toIntOrNull() ?: hostConfigState.id)
        return Response.success(202, hostConfigState)
    }

    override suspend fun hostConfigById(id: Int): Response<HostConfigResource> {
        before("hostConfigById")
        if (hostConfigState.id != id) return notFound()
        return Response.success(hostConfigState)
    }

    override suspend fun localization(): JsonObject {
        before("localization")
        return localizationJson
    }

    override suspend fun localizationLanguage(): LocalizationLanguageResource {
        before("localizationLanguage")
        return localizationLanguageResult
    }

    override suspend fun log(
        page: Int?,
        pageSize: Int?,
        sortKey: String?,
        sortDirection: SortDirection?,
        level: String?,
    ): LogResourcePagingResource {
        before("log")
        val filtered = logItems.filter { level == null || it.level == level }
        return LogResourcePagingResource(
            page = page ?: 1,
            pageSize = pageSize ?: 10,
            sortKey = sortKey,
            sortDirection = sortDirection ?: SortDirection.DEFAULT,
            totalRecords = filtered.size,
            records = paged(filtered, page, pageSize),
        )
    }

    override suspend fun logFiles(): List<LogFileResource> {
        before("logFiles")
        return logFileItems.toList()
    }

    override suspend fun logFileContents(filename: String): ResponseBody {
        before("logFileContents")
        return text(logFileContentsText)
    }

    override suspend fun updateLogFiles(): List<LogFileResource> {
        before("updateLogFiles")
        return updateLogFileItems.toList()
    }

    override suspend fun updateLogFileContents(filename: String): ResponseBody {
        before("updateLogFileContents")
        return text(updateLogFileContentsText)
    }

    override suspend fun mediaManagementConfig(): MediaManagementConfigResource {
        before("mediaManagementConfig")
        return mediaManagementConfigState
    }

    override suspend fun updateMediaManagementConfig(
        id: String,
        body: MediaManagementConfigResource,
    ): Response<MediaManagementConfigResource> {
        before("updateMediaManagementConfig")
        mediaManagementConfigState = body.copy(id = id.toIntOrNull() ?: mediaManagementConfigState.id)
        return Response.success(202, mediaManagementConfigState)
    }

    override suspend fun mediaManagementConfigById(id: Int): Response<MediaManagementConfigResource> {
        before("mediaManagementConfigById")
        if (mediaManagementConfigState.id != id) return notFound()
        return Response.success(mediaManagementConfigState)
    }

    override suspend fun metadata(): List<MetadataResource> {
        before("metadata")
        return metadataProviders.toList()
    }

    override suspend fun createMetadata(
        forceSave: Boolean?,
        body: MetadataResource,
    ): Response<MetadataResource> {
        before("createMetadata")
        val created = body.copy(id = (metadataProviders.maxOfOrNull { it.id } ?: 0) + 1)
        metadataProviders += created
        return Response.success(201, created)
    }

    override suspend fun updateMetadata(
        id: Int,
        forceSave: Boolean?,
        body: MetadataResource,
    ): Response<MetadataResource> {
        before("updateMetadata")
        val index = metadataProviders.indexOfFirst { it.id == id }
        if (index < 0) return notFound()
        metadataProviders[index] = body.copy(id = id)
        return Response.success(202, metadataProviders[index])
    }

    override suspend fun removeMetadata(id: Int): Response<Unit> {
        before("removeMetadata")
        if (metadataProviders.none { it.id == id }) return notFound()
        metadataProviders.removeAll { it.id == id }
        return Response.success(Unit)
    }

    override suspend fun metadataById(id: Int): Response<MetadataResource> {
        before("metadataById")
        return metadataProviders.firstOrNull { it.id == id }
            ?.let { Response.success(it) } ?: notFound()
    }

    override suspend fun metadataSchema(): List<MetadataResource> {
        before("metadataSchema")
        return metadataProviders.filter { it.id == 0 }.toList()
    }

    override suspend fun testMetadata(forceTest: Boolean?, body: MetadataResource): Response<Unit> {
        before("testMetadata")
        return Response.success(204, Unit)
    }

    override suspend fun testAllMetadata(): Response<List<ProviderTestAllResult>> {
        before("testAllMetadata")
        return Response.success(metadataTestResults.toList())
    }

    override suspend fun metadataAction(name: String, body: MetadataResource): Response<Unit> {
        before("metadataAction")
        return Response.success(Unit)
    }

    override suspend fun metadataConfig(): MetadataConfigResource {
        before("metadataConfig")
        return metadataConfigState
    }

    override suspend fun updateMetadataConfig(
        id: String,
        body: MetadataConfigResource,
    ): Response<MetadataConfigResource> {
        before("updateMetadataConfig")
        metadataConfigState = body.copy(id = id.toIntOrNull() ?: metadataConfigState.id)
        return Response.success(202, metadataConfigState)
    }

    override suspend fun metadataConfigById(id: Int): Response<MetadataConfigResource> {
        before("metadataConfigById")
        if (metadataConfigState.id != id) return notFound()
        return Response.success(metadataConfigState)
    }

    override suspend fun namingConfig(): NamingConfigResource {
        before("namingConfig")
        return namingConfigState
    }

    override suspend fun updateNamingConfig(
        id: String,
        body: NamingConfigResource,
    ): Response<NamingConfigResource> {
        before("updateNamingConfig")
        namingConfigState = body.copy(id = id.toIntOrNull() ?: namingConfigState.id)
        return Response.success(202, namingConfigState)
    }

    override suspend fun namingConfigById(id: Int): Response<NamingConfigResource> {
        before("namingConfigById")
        if (namingConfigState.id != id) return notFound()
        return Response.success(namingConfigState)
    }

    override suspend fun namingExamples(
        renameMovies: Boolean?,
        replaceIllegalCharacters: Boolean?,
        colonReplacementFormat: ColonReplacementFormat?,
        standardMovieFormat: String?,
        movieFolderFormat: String?,
        id: Int?,
        resourceName: String?,
    ): NamingExampleResource {
        before("namingExamples")
        return namingExamplesResult
    }

    override suspend fun parse(title: String?): ParseResource {
        before("parse")
        return parseResultsByTitle[title] ?: ParseResource(title = title)
    }

    override suspend fun ping(): Response<PingResource> {
        before("ping")
        return Response.success(pingResult)
    }

    override suspend fun createRootFolder(body: RootFolderResource): Response<RootFolderResource> {
        before("createRootFolder")
        val created = body.copy(id = (rootFolderItems.maxOfOrNull { it.id } ?: 0) + 1)
        rootFolderItems += created
        return Response.success(201, created)
    }

    override suspend fun rootFolders(): List<RootFolderResource> {
        before("rootFolders")
        return rootFolderItems.toList()
    }

    override suspend fun removeRootFolder(id: Int): Response<Unit> {
        before("removeRootFolder")
        if (rootFolderItems.none { it.id == id }) return notFound()
        rootFolderItems.removeAll { it.id == id }
        return Response.success(Unit)
    }

    override suspend fun rootFolder(id: Int): Response<RootFolderResource> {
        before("rootFolder")
        return rootFolderItems.firstOrNull { it.id == id }
            ?.let { Response.success(it) } ?: notFound()
    }

    override suspend fun systemStatus(): SystemResource {
        before("systemStatus")
        return systemStatusResult
    }

    override suspend fun systemRoutes(): ResponseBody {
        before("systemRoutes")
        return text(systemRoutesText)
    }

    override suspend fun systemRoutesDuplicate(): Map<String, List<String>> {
        before("systemRoutesDuplicate")
        return systemRoutesDuplicateResult
    }

    override suspend fun shutdown(): SystemShutdownResource {
        before("shutdown")
        shutdownRequested = true
        return SystemShutdownResource(shuttingDown = true)
    }

    override suspend fun restart(): SystemRestartResource {
        before("restart")
        restartRequested = true
        return SystemRestartResource(restarting = true)
    }

    override suspend fun tags(): List<TagResource> {
        before("tags")
        return tagItems.toList()
    }

    override suspend fun createTag(body: TagResource): Response<TagResource> {
        before("createTag")
        val created = body.copy(id = (tagItems.maxOfOrNull { it.id } ?: 0) + 1)
        tagItems += created
        return Response.success(201, created)
    }

    override suspend fun updateTag(id: String, body: TagResource): Response<TagResource> {
        before("updateTag")
        val numericId = id.toIntOrNull() ?: return notFound()
        val index = tagItems.indexOfFirst { it.id == numericId }
        if (index < 0) return notFound()
        tagItems[index] = body.copy(id = numericId)
        return Response.success(202, tagItems[index])
    }

    override suspend fun removeTag(id: Int): Response<Unit> {
        before("removeTag")
        if (tagItems.none { it.id == id }) return notFound()
        tagItems.removeAll { it.id == id }
        return Response.success(Unit)
    }

    override suspend fun tag(id: Int): Response<TagResource> {
        before("tag")
        return tagItems.firstOrNull { it.id == id }
            ?.let { Response.success(it) } ?: notFound()
    }

    override suspend fun tagDetails(): List<TagDetailsResource> {
        before("tagDetails")
        return tagItems.map(::deriveTagDetails)
    }

    override suspend fun tagDetail(id: Int): Response<TagDetailsResource> {
        before("tagDetail")
        return tagItems.firstOrNull { it.id == id }
            ?.let { Response.success(deriveTagDetails(it)) } ?: notFound()
    }

    private fun deriveTagDetails(tag: TagResource): TagDetailsResource {
        val usage = tagUsage[tag.id]
        return TagDetailsResource(
            id = tag.id,
            label = tag.label,
            delayProfileIds = usage?.delayProfileIds ?: emptyList(),
            importListIds = usage?.importListIds ?: emptyList(),
            notificationIds = usage?.notificationIds ?: emptyList(),
            releaseProfileIds = usage?.releaseProfileIds ?: emptyList(),
            indexerIds = usage?.indexerIds ?: emptyList(),
            downloadClientIds = usage?.downloadClientIds ?: emptyList(),
            autoTagIds = usage?.autoTagIds ?: emptyList(),
            movieIds = usage?.movieIds ?: emptyList(),
        )
    }

    override suspend fun tasks(): List<TaskResource> {
        before("tasks")
        return taskItems.toList()
    }

    override suspend fun task(id: Int): Response<TaskResource> {
        before("task")
        return taskItems.firstOrNull { it.id == id }
            ?.let { Response.success(it) } ?: notFound()
    }

    override suspend fun updateUiConfig(id: String, body: UiConfigResource): Response<UiConfigResource> {
        before("updateUiConfig")
        uiConfigState = body.copy(id = id.toIntOrNull() ?: uiConfigState.id)
        return Response.success(202, uiConfigState)
    }

    override suspend fun uiConfigById(id: Int): Response<UiConfigResource> {
        before("uiConfigById")
        if (uiConfigState.id != id) return notFound()
        return Response.success(uiConfigState)
    }

    override suspend fun uiConfig(): UiConfigResource {
        before("uiConfig")
        return uiConfigState
    }

    override suspend fun updates(): List<UpdateResource> {
        before("updates")
        return updateItems.toList()
    }

    private fun <T> paged(items: List<T>, page: Int?, pageSize: Int?): List<T> {
        val p = page ?: 1
        val size = pageSize ?: 10
        require(p >= 1 && size >= 1) { "Page and page size must be positive" }
        val start = ((p.toLong() - 1) * size).coerceAtMost(items.size.toLong()).toInt()
        return items.drop(start).take(size)
    }
}
