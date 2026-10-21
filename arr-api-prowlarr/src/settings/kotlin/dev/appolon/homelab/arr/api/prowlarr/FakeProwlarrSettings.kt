package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.ApplyTags
import dev.appolon.homelab.arr.api.prowlarr.model.DevelopmentConfigResource
import dev.appolon.homelab.arr.api.prowlarr.model.DownloadClientBulkResource
import dev.appolon.homelab.arr.api.prowlarr.model.DownloadClientConfigResource
import dev.appolon.homelab.arr.api.prowlarr.model.DownloadClientResource
import dev.appolon.homelab.arr.api.prowlarr.model.HostConfigResource
import dev.appolon.homelab.arr.api.prowlarr.model.LocalizationOption
import dev.appolon.homelab.arr.api.prowlarr.model.NotificationResource
import dev.appolon.homelab.arr.api.prowlarr.model.UiConfigResource
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import retrofit2.HttpException
import retrofit2.Response

/** One recorded fake invocation with its wire-shaped arguments. */
data class ProwlarrSettingsCall(val operation: String, val arguments: Map<String, Any?>)

/**
 * In-memory implementation of every settings-group interface for UI tests.
 * Seed the state properties, then read through any interface: config
 * singletons round-trip, download clients and notifications behave like the
 * server's CRUD, so settings screens can be driven end to end.
 *
 * [latency] is suspending so tests inject delay without blocking; [failure]
 * throws before any state changes (transport or cancellation failures); and
 * [errorStatus] turns one call into an HTTP 4xx or 5xx, thrown as
 * [HttpException] for plain calls and returned inside [Response] for calls
 * whose signatures carry the status. [calls] records every invocation for
 * assertion. Config GETs ignore the path id like the server's singletons;
 * download client and notification reads of unknown ids throw the matching
 * 404. Updates upsert, which keeps edit flows testable without seeding
 * every row. Configure and use the fake from one test dispatcher.
 */
class FakeProwlarrSettings(
    var latency: suspend (ProwlarrSettingsCall) -> Unit = {},
    var errorStatus: (ProwlarrSettingsCall) -> Int? = { null },
    var failure: (ProwlarrSettingsCall) -> Throwable? = { null },
) : DownloadClientApi,
    DownloadClientConfigApi,
    NotificationApi,
    HostConfigApi,
    DevelopmentConfigApi,
    UiConfigApi,
    AuthenticationApi,
    FileSystemApi,
    LocalizationApi {

    val calls: MutableList<ProwlarrSettingsCall> = mutableListOf()
    val downloadClients = mutableMapOf<Int, DownloadClientResource>()
    val downloadClientSchemas = mutableListOf<DownloadClientResource>()
    var downloadClientConfig: DownloadClientConfigResource = DownloadClientConfigResource()
    val notifications = mutableMapOf<Int, NotificationResource>()
    val notificationSchemas = mutableListOf<NotificationResource>()
    var hostConfig: HostConfigResource = HostConfigResource()
    var developmentConfig: DevelopmentConfigResource = DevelopmentConfigResource()
    var uiConfig: UiConfigResource = UiConfigResource()
    val localizationOptions = mutableListOf<LocalizationOption>()
    var localizationDictionary: JsonObject = JsonObject(emptyMap())
    val fileSystemListings = mutableMapOf<String, JsonObject>()
    val fileSystemTypes = mutableMapOf<String, JsonElement>()

    /** True after a successful [login], false after [logout]. */
    var authenticated = false

    /** Ids handed to creates whose resource carries no id. */
    var nextId = 100

    private suspend fun gate(operation: String, vararg arguments: Pair<String, Any?>): Int? {
        val call = ProwlarrSettingsCall(operation, arguments.toMap())
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

    /** Part contents as text, so tests assert form values instead of bodies. */
    private fun partText(body: RequestBody?): String? = body?.let { part ->
        runCatching {
            val buffer = Buffer()
            part.writeTo(buffer)
            buffer.readUtf8()
        }.getOrNull()
    }

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
        map[pathId.toIntOrNull() ?: resourceId] = resource
        return resource
    }

    override suspend fun getDownloadClients(): List<DownloadClientResource> {
        read("getDownloadClients")
        return downloadClients.values.toList()
    }

    override suspend fun getDownloadClient(id: Int): DownloadClientResource {
        read("getDownloadClient", "id" to id)
        return stored(downloadClients, id)
    }

    override suspend fun getDownloadClientSchemas(): List<DownloadClientResource> {
        read("getDownloadClientSchemas")
        return downloadClientSchemas.toList()
    }

    override suspend fun createDownloadClient(
        forceSave: Boolean?,
        resource: DownloadClientResource,
    ): DownloadClientResource {
        read("createDownloadClient", "forceSave" to forceSave, "resource" to resource)
        val stored = resource.copy(id = freeId(resource.id))
        downloadClients[stored.id] = stored
        return stored
    }

    override suspend fun updateDownloadClient(
        id: String,
        forceSave: Boolean?,
        resource: DownloadClientResource,
    ): DownloadClientResource {
        read("updateDownloadClient", "id" to id, "forceSave" to forceSave, "resource" to resource)
        return upsert(downloadClients, id, resource.id, resource)
    }

    override suspend fun updateDownloadClients(
        resource: DownloadClientBulkResource,
    ): DownloadClientResource {
        read("updateDownloadClients", "resource" to resource)
        val updated = (resource.ids ?: emptyList()).mapNotNull { id ->
            downloadClients[id]?.let { current ->
                downloadClients[id] = current.copy(
                    enable = resource.enable ?: current.enable,
                    priority = resource.priority ?: current.priority,
                    tags = mergeTags(current.tags, resource.tags, resource.applyTags),
                )
                downloadClients[id]
            }
        }
        return updated.firstOrNull() ?: DownloadClientResource()
    }

    override suspend fun deleteDownloadClient(id: Int): Response<Unit> {
        val status = gate("deleteDownloadClient", "id" to id)
        downloadClients.remove(id)
        return status?.let { Response.error(it, "{}".toResponseBody()) }
            ?: Response.success(200, Unit)
    }

    override suspend fun deleteDownloadClients(
        resource: DownloadClientBulkResource,
    ): Response<Unit> {
        val status = gate("deleteDownloadClients", "resource" to resource)
        if (status != null) return Response.error(status, "{}".toResponseBody())
        (resource.ids ?: emptyList()).forEach(downloadClients::remove)
        return Response.success(200, Unit)
    }

    override suspend fun testDownloadClient(
        forceTest: Boolean?,
        resource: DownloadClientResource,
    ): Response<Unit> =
        respond("testDownloadClient", "forceTest" to forceTest, "resource" to resource)

    override suspend fun testAllDownloadClients(): Response<Unit> =
        respond("testAllDownloadClients")

    override suspend fun downloadClientAction(
        name: String,
        resource: DownloadClientResource,
    ): Response<Unit> =
        respond("downloadClientAction", "name" to name, "resource" to resource)

    override suspend fun getDownloadClientConfig(): DownloadClientConfigResource {
        read("getDownloadClientConfig")
        return downloadClientConfig
    }

    override suspend fun getDownloadClientConfig(id: Int): DownloadClientConfigResource {
        read("getDownloadClientConfig", "id" to id)
        return downloadClientConfig
    }

    override suspend fun updateDownloadClientConfig(
        id: String,
        resource: DownloadClientConfigResource,
    ): DownloadClientConfigResource {
        read("updateDownloadClientConfig", "id" to id, "resource" to resource)
        downloadClientConfig = resource
        return resource
    }

    override suspend fun getNotifications(): List<NotificationResource> {
        read("getNotifications")
        return notifications.values.toList()
    }

    override suspend fun getNotification(id: Int): NotificationResource {
        read("getNotification", "id" to id)
        return stored(notifications, id)
    }

    override suspend fun getNotificationSchemas(): List<NotificationResource> {
        read("getNotificationSchemas")
        return notificationSchemas.toList()
    }

    override suspend fun createNotification(
        forceSave: Boolean?,
        resource: NotificationResource,
    ): NotificationResource {
        read("createNotification", "forceSave" to forceSave, "resource" to resource)
        val stored = resource.copy(id = freeId(resource.id))
        notifications[stored.id] = stored
        return stored
    }

    override suspend fun updateNotification(
        id: String,
        forceSave: Boolean?,
        resource: NotificationResource,
    ): NotificationResource {
        read("updateNotification", "id" to id, "forceSave" to forceSave, "resource" to resource)
        return upsert(notifications, id, resource.id, resource)
    }

    override suspend fun deleteNotification(id: Int): Response<Unit> {
        val status = gate("deleteNotification", "id" to id)
        notifications.remove(id)
        return status?.let { Response.error(it, "{}".toResponseBody()) }
            ?: Response.success(200, Unit)
    }

    override suspend fun testNotification(
        forceTest: Boolean?,
        resource: NotificationResource,
    ): Response<Unit> =
        respond("testNotification", "forceTest" to forceTest, "resource" to resource)

    override suspend fun testAllNotifications(): Response<Unit> =
        respond("testAllNotifications")

    override suspend fun notificationAction(
        name: String,
        resource: NotificationResource,
    ): Response<Unit> = respond("notificationAction", "name" to name, "resource" to resource)

    override suspend fun getHostConfig(): HostConfigResource {
        read("getHostConfig")
        return hostConfig
    }

    override suspend fun getHostConfig(id: Int): HostConfigResource {
        read("getHostConfig", "id" to id)
        return hostConfig
    }

    override suspend fun updateHostConfig(
        id: String,
        resource: HostConfigResource,
    ): HostConfigResource {
        read("updateHostConfig", "id" to id, "resource" to resource)
        hostConfig = resource
        return resource
    }

    override suspend fun getDevelopmentConfig(): DevelopmentConfigResource {
        read("getDevelopmentConfig")
        return developmentConfig
    }

    override suspend fun getDevelopmentConfig(id: Int): DevelopmentConfigResource {
        read("getDevelopmentConfig", "id" to id)
        return developmentConfig
    }

    override suspend fun updateDevelopmentConfig(
        id: String,
        resource: DevelopmentConfigResource,
    ): DevelopmentConfigResource {
        read("updateDevelopmentConfig", "id" to id, "resource" to resource)
        developmentConfig = resource
        return resource
    }

    override suspend fun getUiConfig(): UiConfigResource {
        read("getUiConfig")
        return uiConfig
    }

    override suspend fun getUiConfig(id: Int): UiConfigResource {
        read("getUiConfig", "id" to id)
        return uiConfig
    }

    override suspend fun updateUiConfig(
        id: String,
        resource: UiConfigResource,
    ): UiConfigResource {
        read("updateUiConfig", "id" to id, "resource" to resource)
        uiConfig = resource
        return resource
    }

    override suspend fun login(
        username: RequestBody,
        password: RequestBody,
        rememberMe: RequestBody?,
        returnUrl: String?,
    ): Response<Unit> {
        val status = respond(
            "login",
            "username" to partText(username),
            "password" to partText(password),
            "rememberMe" to partText(rememberMe),
            "returnUrl" to returnUrl,
        )
        if (status.isSuccessful) authenticated = true
        return status
    }

    override suspend fun logout(): Response<Unit> {
        val status = respond("logout")
        if (status.isSuccessful) authenticated = false
        return status
    }

    override suspend fun getFileSystem(
        path: String?,
        includeFiles: Boolean?,
        allowFoldersWithoutTrailingSlashes: Boolean?,
    ): JsonObject {
        read(
            "getFileSystem",
            "path" to path,
            "includeFiles" to includeFiles,
            "allowFoldersWithoutTrailingSlashes" to allowFoldersWithoutTrailingSlashes,
        )
        return fileSystemListings[path] ?: JsonObject(emptyMap())
    }

    override suspend fun getFileSystemType(path: String?): JsonElement {
        read("getFileSystemType", "path" to path)
        return fileSystemTypes[path] ?: JsonPrimitive("folder")
    }

    override suspend fun getLocalization(): JsonObject {
        read("getLocalization")
        return localizationDictionary
    }

    override suspend fun getLocalizationOptions(): List<LocalizationOption> {
        read("getLocalizationOptions")
        return localizationOptions.toList()
    }
}
