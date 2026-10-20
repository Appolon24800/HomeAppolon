package dev.appolon.homelab.arr.feature.lidarr.settings.system

import dev.appolon.homelab.arr.api.lidarr.AutoTaggingApi
import dev.appolon.homelab.arr.api.lidarr.BackupApi
import dev.appolon.homelab.arr.api.lidarr.CommandApi
import dev.appolon.homelab.arr.api.lidarr.DiskSpaceApi
import dev.appolon.homelab.arr.api.lidarr.HealthApi
import dev.appolon.homelab.arr.api.lidarr.HostConfigApi
import dev.appolon.homelab.arr.api.lidarr.LidarrQueueApis
import dev.appolon.homelab.arr.api.lidarr.LidarrSystemApis
import dev.appolon.homelab.arr.api.lidarr.SystemApi
import dev.appolon.homelab.arr.api.lidarr.TagApi
import dev.appolon.homelab.arr.api.lidarr.TagDetailsApi
import dev.appolon.homelab.arr.api.lidarr.TaskApi
import dev.appolon.homelab.arr.api.lidarr.UpdateApi
import dev.appolon.homelab.arr.api.lidarr.UpdateLogFileApi
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
 * Everything the general, system, backup, updates, and tags screens need to
 * talk to one Lidarr server, built on the committed client stack. Mirrors
 * the connect settings connection: one Retrofit over the shared per-server
 * OkHttp client, with the system API factories supplying every interface.
 * The command API comes from the queue factories because backup creation
 * and update installation are dispatched as named commands.
 */
class LidarrSettingsSystemConnection(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {

    /** The per-server API bundle; null when the server or its secrets are gone. */
    suspend fun connect(serverId: ServerId): LidarrSettingsSystemApis? {
        val server = registry.byId(serverId) ?: return null
        val endpoint = endpointOf(server) ?: return null
        val retrofit = Retrofit.Builder()
            .baseUrl(ServerEndpoint.normalizeBaseUrl(server.baseUrl))
            .client(clientFactory.clientFor(endpoint))
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        return LidarrSettingsSystemApis(
            hostConfigApi = LidarrSystemApis.hostConfig(retrofit),
            backupApi = LidarrSystemApis.backup(retrofit),
            updateApi = LidarrSystemApis.update(retrofit),
            updateLogFileApi = LidarrSystemApis.updateLogFile(retrofit),
            systemApi = LidarrSystemApis.system(retrofit),
            taskApi = LidarrSystemApis.task(retrofit),
            diskSpaceApi = LidarrSystemApis.diskSpace(retrofit),
            healthApi = LidarrSystemApis.health(retrofit),
            tagApi = LidarrSystemApis.tag(retrofit),
            tagDetailsApi = LidarrSystemApis.tagDetails(retrofit),
            autoTaggingApi = LidarrSystemApis.autoTagging(retrofit),
            commandApi = LidarrQueueApis.command(retrofit),
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
 * Per-server API bundle handed to the repository of this group: the host
 * config, backups, updates and their log files, system status, scheduled
 * tasks, disk space, health checks, tags with usage details, auto tagging
 * rules, and the command endpoint backup and update actions dispatch
 * through.
 */
class LidarrSettingsSystemApis(
    val hostConfigApi: HostConfigApi,
    val backupApi: BackupApi,
    val updateApi: UpdateApi,
    val updateLogFileApi: UpdateLogFileApi,
    val systemApi: SystemApi,
    val taskApi: TaskApi,
    val diskSpaceApi: DiskSpaceApi,
    val healthApi: HealthApi,
    val tagApi: TagApi,
    val tagDetailsApi: TagDetailsApi,
    val autoTaggingApi: AutoTaggingApi,
    val commandApi: CommandApi,
)
