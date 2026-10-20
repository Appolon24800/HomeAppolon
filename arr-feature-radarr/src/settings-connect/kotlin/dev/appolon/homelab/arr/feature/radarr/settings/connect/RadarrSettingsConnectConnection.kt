package dev.appolon.homelab.arr.feature.radarr.settings.connect

import dev.appolon.homelab.arr.api.radarr.CommandApi
import dev.appolon.homelab.arr.api.radarr.DownloadClientApi
import dev.appolon.homelab.arr.api.radarr.DownloadClientConfigApi
import dev.appolon.homelab.arr.api.radarr.ImportListApi
import dev.appolon.homelab.arr.api.radarr.ImportListConfigApi
import dev.appolon.homelab.arr.api.radarr.ImportListExclusionApi
import dev.appolon.homelab.arr.api.radarr.ImportListMoviesApi
import dev.appolon.homelab.arr.api.radarr.IndexerApi
import dev.appolon.homelab.arr.api.radarr.IndexerConfigApi
import dev.appolon.homelab.arr.api.radarr.IndexerFlagApi
import dev.appolon.homelab.arr.api.radarr.MetadataApi
import dev.appolon.homelab.arr.api.radarr.NotificationApi
import dev.appolon.homelab.arr.api.radarr.RadarrConnectApis
import dev.appolon.homelab.arr.api.radarr.RadarrImportlistApis
import dev.appolon.homelab.arr.api.radarr.RadarrQueueApis
import dev.appolon.homelab.arr.api.radarr.RadarrSystemApis
import dev.appolon.homelab.arr.api.radarr.RemotePathMappingApi
import dev.appolon.homelab.arr.api.radarr.TagApi
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
 * Everything the connect settings screens need to talk to one Radarr
 * server, built on the committed client stack. Mirrors the media
 * management and quality connections: one Retrofit over the shared
 * per-server OkHttp client, with the connect, import list, queue, and
 * system API factories supplying every interface this group uses.
 */
class RadarrSettingsConnectConnection(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {

    /** The per-server API bundle; null when the server or its secrets are gone. */
    suspend fun connect(serverId: ServerId): RadarrSettingsConnectApis? {
        val server = registry.byId(serverId) ?: return null
        val endpoint = endpointOf(server) ?: return null
        val retrofit = Retrofit.Builder()
            .baseUrl(ServerEndpoint.normalizeBaseUrl(server.baseUrl))
            .client(clientFactory.clientFor(endpoint))
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        return RadarrSettingsConnectApis(
            indexerApi = RadarrConnectApis.indexer(retrofit),
            indexerConfigApi = RadarrConnectApis.indexerConfig(retrofit),
            indexerFlagApi = RadarrConnectApis.indexerFlag(retrofit),
            downloadClientApi = RadarrConnectApis.downloadClient(retrofit),
            downloadClientConfigApi = RadarrConnectApis.downloadClientConfig(retrofit),
            notificationApi = RadarrConnectApis.notification(retrofit),
            remotePathMappingApi = RadarrConnectApis.remotePathMapping(retrofit),
            importListApi = RadarrImportlistApis.importList(retrofit),
            importListConfigApi = RadarrImportlistApis.importListConfig(retrofit),
            importListExclusionApi = RadarrImportlistApis.importListExclusion(retrofit),
            importListMoviesApi = RadarrImportlistApis.importListMovies(retrofit),
            metadataApi = RadarrSystemApis.metadata(retrofit),
            commandApi = RadarrQueueApis.command(retrofit),
            tagApi = RadarrSystemApis.tag(retrofit),
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
 * Per-server API bundle handed to the repository of this group: indexers
 * with their config and flags, download clients with their config and
 * remote path mappings, import lists with their config, exclusions, and
 * sync preview, connect notifications, metadata consumers, the command
 * endpoint for list sync, and tags for pickers.
 */
class RadarrSettingsConnectApis(
    val indexerApi: IndexerApi,
    val indexerConfigApi: IndexerConfigApi,
    val indexerFlagApi: IndexerFlagApi,
    val downloadClientApi: DownloadClientApi,
    val downloadClientConfigApi: DownloadClientConfigApi,
    val notificationApi: NotificationApi,
    val remotePathMappingApi: RemotePathMappingApi,
    val importListApi: ImportListApi,
    val importListConfigApi: ImportListConfigApi,
    val importListExclusionApi: ImportListExclusionApi,
    val importListMoviesApi: ImportListMoviesApi,
    val metadataApi: MetadataApi,
    val commandApi: CommandApi,
    val tagApi: TagApi,
)
