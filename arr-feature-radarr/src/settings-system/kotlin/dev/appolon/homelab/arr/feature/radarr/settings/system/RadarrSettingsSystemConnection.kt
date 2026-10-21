package dev.appolon.homelab.arr.feature.radarr.settings.system

import dev.appolon.homelab.arr.api.radarr.AutoTaggingApi
import dev.appolon.homelab.arr.api.radarr.BackupApi
import dev.appolon.homelab.arr.api.radarr.CommandApi
import dev.appolon.homelab.arr.api.radarr.DiskSpaceApi
import dev.appolon.homelab.arr.api.radarr.HealthApi
import dev.appolon.homelab.arr.api.radarr.HostConfigApi
import dev.appolon.homelab.arr.api.radarr.RadarrQueueApis
import dev.appolon.homelab.arr.api.radarr.RadarrSystemApis
import dev.appolon.homelab.arr.api.radarr.TagApi
import dev.appolon.homelab.arr.api.radarr.TagDetailsApi
import dev.appolon.homelab.arr.api.radarr.TaskApi
import dev.appolon.homelab.arr.api.radarr.SystemApi
import dev.appolon.homelab.arr.api.radarr.UpdateApi
import dev.appolon.homelab.arr.api.radarr.UpdateLogFileApi
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
 * talk to one Radarr server, built on the committed client stack. Mirrors
 * the media management connection: one Retrofit over the shared per-server
 * OkHttp client, with the system API factories supplying every interface.
 * The command API comes from the queue factories because backup creation and
 * update installation are dispatched as named commands.
 */
class RadarrSettingsSystemConnection(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {

    /** The per-server API bundle; null when the server or its secrets are gone. */
    suspend fun connect(serverId: ServerId): RadarrSettingsSystemApis? {
        val server = registry.byId(serverId) ?: return null
        val endpoint = endpointOf(server) ?: return null
        val retrofit = Retrofit.Builder()
            .baseUrl(ServerEndpoint.normalizeBaseUrl(server.baseUrl))
            .client(clientFactory.clientFor(endpoint))
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        return RadarrSettingsSystemApis(
            hostConfigApi = RadarrSystemApis.hostConfig(retrofit),
            backupApi = RadarrSystemApis.backup(retrofit),
            updateApi = RadarrSystemApis.update(retrofit),
            updateLogFileApi = RadarrSystemApis.updateLogFile(retrofit),
            systemApi = RadarrSystemApis.system(retrofit),
            taskApi = RadarrSystemApis.task(retrofit),
            diskSpaceApi = RadarrSystemApis.diskSpace(retrofit),
            healthApi = RadarrSystemApis.health(retrofit),
            tagApi = RadarrSystemApis.tag(retrofit),
            tagDetailsApi = RadarrSystemApis.tagDetails(retrofit),
            autoTaggingApi = RadarrSystemApis.autoTagging(retrofit),
            commandApi = RadarrQueueApis.command(retrofit),
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
class RadarrSettingsSystemApis(
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
