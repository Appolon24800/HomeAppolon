package dev.appolon.homelab.arr.feature.sonarr.settings.system

import dev.appolon.homelab.arr.api.sonarr.AutoTaggingApi
import dev.appolon.homelab.arr.api.sonarr.BackupApi
import dev.appolon.homelab.arr.api.sonarr.CommandApi
import dev.appolon.homelab.arr.api.sonarr.DiskSpaceApi
import dev.appolon.homelab.arr.api.sonarr.HealthApi
import dev.appolon.homelab.arr.api.sonarr.HostConfigApi
import dev.appolon.homelab.arr.api.sonarr.SonarrQueueApis
import dev.appolon.homelab.arr.api.sonarr.SonarrSystemApis
import dev.appolon.homelab.arr.api.sonarr.SystemApi
import dev.appolon.homelab.arr.api.sonarr.TagApi
import dev.appolon.homelab.arr.api.sonarr.TagDetailsApi
import dev.appolon.homelab.arr.api.sonarr.TaskApi
import dev.appolon.homelab.arr.api.sonarr.UpdateApi
import dev.appolon.homelab.arr.api.sonarr.UpdateLogFileApi
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
 * talk to one Sonarr server, built on the committed client stack. Mirrors
 * the media management connection: one Retrofit over the shared per-server
 * OkHttp client, with the system API factories supplying every interface.
 * The command API comes from the queue factories because backup creation
 * and update installation are dispatched as named commands.
 */
class SonarrSettingsSystemConnection(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {

    /** The per-server API bundle; null when the server or its secrets are gone. */
    suspend fun connect(serverId: ServerId): SonarrSettingsSystemApis? {
        val server = registry.byId(serverId) ?: return null
        val endpoint = endpointOf(server) ?: return null
        val retrofit = Retrofit.Builder()
            .baseUrl(ServerEndpoint.normalizeBaseUrl(server.baseUrl))
            .client(clientFactory.clientFor(endpoint))
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        return SonarrSettingsSystemApis(
            hostConfigApi = SonarrSystemApis.hostConfig(retrofit),
            backupApi = SonarrSystemApis.backup(retrofit),
            updateApi = SonarrSystemApis.update(retrofit),
            updateLogFileApi = SonarrSystemApis.updateLogFile(retrofit),
            systemApi = SonarrSystemApis.system(retrofit),
            taskApi = SonarrSystemApis.task(retrofit),
            diskSpaceApi = SonarrSystemApis.diskSpace(retrofit),
            healthApi = SonarrSystemApis.health(retrofit),
            tagApi = SonarrSystemApis.tag(retrofit),
            tagDetailsApi = SonarrSystemApis.tagDetails(retrofit),
            autoTaggingApi = SonarrSystemApis.autoTagging(retrofit),
            commandApi = SonarrQueueApis.command(retrofit),
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
class SonarrSettingsSystemApis(
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
