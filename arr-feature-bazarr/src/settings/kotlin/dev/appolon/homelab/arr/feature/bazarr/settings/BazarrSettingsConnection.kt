package dev.appolon.homelab.arr.feature.bazarr.settings

import dev.appolon.homelab.arr.api.bazarr.BazarrSubtitlesApis
import dev.appolon.homelab.arr.api.bazarr.BazarrSystemApis
import dev.appolon.homelab.arr.api.bazarr.ProvidersApi
import dev.appolon.homelab.arr.api.bazarr.SystemBackupsApi
import dev.appolon.homelab.arr.api.bazarr.SystemJobsApi
import dev.appolon.homelab.arr.api.bazarr.SystemLanguagesApi
import dev.appolon.homelab.arr.api.bazarr.SystemLanguagesProfilesApi
import dev.appolon.homelab.arr.api.bazarr.SystemLogsApi
import dev.appolon.homelab.arr.api.bazarr.SystemSettingsApi
import dev.appolon.homelab.arr.api.bazarr.SystemStatusApi
import dev.appolon.homelab.arr.api.bazarr.SystemTasksApi
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
 * Everything the Bazarr settings screens need to talk to one server. One
 * client per server backs every Retrofit service here, so the screens ride
 * the same proxy, auth, and trust path the rest of the app configured for
 * that server. The provider status endpoint lives under the same server
 * root as the system endpoints, so both factories share one Retrofit.
 */
class BazarrSettingsConnection(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {

    /** The per-server API bundle; null when the server or its secrets are gone. */
    suspend fun connect(serverId: ServerId): BazarrSettingsApis? {
        val server = registry.byId(serverId) ?: return null
        val endpoint = endpointOf(server) ?: return null
        val client = clientFactory.clientFor(endpoint)
        val baseUrl = ServerEndpoint.normalizeBaseUrl(server.baseUrl)
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        return BazarrSettingsApis(
            languagesApi = BazarrSystemApis.languages(retrofit),
            languageProfilesApi = BazarrSystemApis.languageProfiles(retrofit),
            tasksApi = BazarrSystemApis.tasks(retrofit),
            jobsApi = BazarrSystemApis.jobs(retrofit),
            statusApi = BazarrSystemApis.status(retrofit),
            backupsApi = BazarrSystemApis.backups(retrofit),
            logsApi = BazarrSystemApis.logs(retrofit),
            webhookSettingsApi = BazarrSystemApis.settings(retrofit),
            providersApi = BazarrSubtitlesApis.providers(retrofit),
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

/** Per-server Retrofit bundle handed to the settings repository. */
class BazarrSettingsApis(
    val languagesApi: SystemLanguagesApi,
    val languageProfilesApi: SystemLanguagesProfilesApi,
    val tasksApi: SystemTasksApi,
    val jobsApi: SystemJobsApi,
    val statusApi: SystemStatusApi,
    val backupsApi: SystemBackupsApi,
    val logsApi: SystemLogsApi,
    val webhookSettingsApi: SystemSettingsApi,
    val providersApi: ProvidersApi,
)
