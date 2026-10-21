package dev.appolon.homelab.arr.feature.prowlarr.settings

import dev.appolon.homelab.arr.api.prowlarr.AuthenticationApi
import dev.appolon.homelab.arr.api.prowlarr.BackupApi
import dev.appolon.homelab.arr.api.prowlarr.DevelopmentConfigApi
import dev.appolon.homelab.arr.api.prowlarr.DownloadClientApi
import dev.appolon.homelab.arr.api.prowlarr.DownloadClientConfigApi
import dev.appolon.homelab.arr.api.prowlarr.FileSystemApi
import dev.appolon.homelab.arr.api.prowlarr.HealthApi
import dev.appolon.homelab.arr.api.prowlarr.HostConfigApi
import dev.appolon.homelab.arr.api.prowlarr.IndexerDefaultCategoriesApi
import dev.appolon.homelab.arr.api.prowlarr.LocalizationApi
import dev.appolon.homelab.arr.api.prowlarr.NotificationApi
import dev.appolon.homelab.arr.api.prowlarr.ProwlarrIndexersApis
import dev.appolon.homelab.arr.api.prowlarr.ProwlarrSettingsApis
import dev.appolon.homelab.arr.api.prowlarr.ProwlarrSystemApis
import dev.appolon.homelab.arr.api.prowlarr.SystemApi
import dev.appolon.homelab.arr.api.prowlarr.TagApi
import dev.appolon.homelab.arr.api.prowlarr.TaskApi
import dev.appolon.homelab.arr.api.prowlarr.UiConfigApi
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.data.ServerRegistry
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
import dev.appolon.homelab.arr.net.ArrClientFactory
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.net.DefaultArrClientFactory
import dev.appolon.homelab.arr.net.ServerEndpoint
import dev.appolon.homelab.arr.net.TrustSpec
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Everything the settings screens need to talk to one Prowlarr server, built
 * on the committed client stack so proxy, basic auth, and trust handling
 * match every other arr feature. One Retrofit per server supplies the
 * settings, system, tag, and default-category interfaces this group uses.
 */
class ProwlarrSettingsConnection(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {

    /** The per-server API bundle; null when the server or its secrets are gone. */
    suspend fun connect(serverId: ServerId): ProwlarrSettingsFeatureApis? {
        val server = registry.byId(serverId) ?: return null
        val endpoint = endpointOf(server) ?: return null
        val retrofit = Retrofit.Builder()
            .baseUrl(ServerEndpoint.normalizeBaseUrl(server.baseUrl))
            .client(clientFactory.clientFor(endpoint))
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        return ProwlarrSettingsFeatureApis(
            downloadClientApi = ProwlarrSettingsApis.downloadClient(retrofit),
            downloadClientConfigApi = ProwlarrSettingsApis.downloadClientConfig(retrofit),
            notificationApi = ProwlarrSettingsApis.notification(retrofit),
            hostConfigApi = ProwlarrSettingsApis.hostConfig(retrofit),
            developmentConfigApi = ProwlarrSettingsApis.developmentConfig(retrofit),
            uiConfigApi = ProwlarrSettingsApis.uiConfig(retrofit),
            authenticationApi = ProwlarrSettingsApis.authentication(retrofit),
            fileSystemApi = ProwlarrSettingsApis.fileSystem(retrofit),
            localizationApi = ProwlarrSettingsApis.localization(retrofit),
            systemApi = ProwlarrSystemApis.system(retrofit),
            taskApi = ProwlarrSystemApis.task(retrofit),
            backupApi = ProwlarrSystemApis.backup(retrofit),
            healthApi = ProwlarrSystemApis.health(retrofit),
            tagApi = ProwlarrIndexersApis.tag(retrofit),
            categoriesApi = ProwlarrIndexersApis.indexerDefaultCategories(retrofit),
        )
    }

    private fun endpointOf(server: ArrServer): ServerEndpoint? = try {
        ServerEndpoint(
            serverId = server.id,
            baseUrl = server.baseUrl,
            apiKey = secrets.decrypt(server.apiKeyEncrypted),
            basicAuthUser = server.basicAuthUser,
            basicAuthPassword = server.basicAuthPasswordEncrypted?.let { secrets.decrypt(it) },
            proxy = server.proxy,
            trust = server.trust ?: TrustSpec.System,
        )
    } catch (t: Throwable) {
        null
    }
}

/**
 * Per-server API bundle handed to this group's repository: download clients
 * with their config, connect notifications, the four config singletons,
 * localization, system status with tasks, backups, health, tags for pickers,
 * and the default newznab categories behind the grab-target mapping.
 */
class ProwlarrSettingsFeatureApis(
    val downloadClientApi: DownloadClientApi,
    val downloadClientConfigApi: DownloadClientConfigApi,
    val notificationApi: NotificationApi,
    val hostConfigApi: HostConfigApi,
    val developmentConfigApi: DevelopmentConfigApi,
    val uiConfigApi: UiConfigApi,
    val authenticationApi: AuthenticationApi,
    val fileSystemApi: FileSystemApi,
    val localizationApi: LocalizationApi,
    val systemApi: SystemApi,
    val taskApi: TaskApi,
    val backupApi: BackupApi,
    val healthApi: HealthApi,
    val tagApi: TagApi,
    val categoriesApi: IndexerDefaultCategoriesApi,
)
