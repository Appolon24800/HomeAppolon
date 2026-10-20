package dev.appolon.homelab.arr.feature.lidarr.settings.connect

import dev.appolon.homelab.arr.api.lidarr.CommandApi
import dev.appolon.homelab.arr.api.lidarr.DownloadClientApi
import dev.appolon.homelab.arr.api.lidarr.DownloadClientConfigApi
import dev.appolon.homelab.arr.api.lidarr.ImportListApi
import dev.appolon.homelab.arr.api.lidarr.ImportListExclusionApi
import dev.appolon.homelab.arr.api.lidarr.IndexerApi
import dev.appolon.homelab.arr.api.lidarr.IndexerConfigApi
import dev.appolon.homelab.arr.api.lidarr.IndexerFlagApi
import dev.appolon.homelab.arr.api.lidarr.LidarrDiscoveryApis
import dev.appolon.homelab.arr.api.lidarr.LidarrQueueApis
import dev.appolon.homelab.arr.api.lidarr.LidarrSystemApis
import dev.appolon.homelab.arr.api.lidarr.MetadataApi
import dev.appolon.homelab.arr.api.lidarr.NotificationApi
import dev.appolon.homelab.arr.api.lidarr.RemotePathMappingApi
import dev.appolon.homelab.arr.api.lidarr.TagApi
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
 * Everything the connect settings screens need to talk to one Lidarr
 * server, built on the committed client stack. Mirrors the media
 * management connection: one Retrofit over the shared per-server OkHttp
 * client, with the system, discovery, and queue API factories supplying
 * every interface this group uses.
 */
class LidarrSettingsConnectConnection(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {

    /** The per-server API bundle; null when the server or its secrets are gone. */
    suspend fun connect(serverId: ServerId): LidarrSettingsConnectApis? {
        val server = registry.byId(serverId) ?: return null
        val endpoint = endpointOf(server) ?: return null
        val retrofit = Retrofit.Builder()
            .baseUrl(ServerEndpoint.normalizeBaseUrl(server.baseUrl))
            .client(clientFactory.clientFor(endpoint))
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        return LidarrSettingsConnectApis(
            indexerApi = LidarrSystemApis.indexer(retrofit),
            indexerConfigApi = LidarrSystemApis.indexerConfig(retrofit),
            indexerFlagApi = LidarrSystemApis.indexerFlag(retrofit),
            downloadClientApi = LidarrSystemApis.downloadClient(retrofit),
            downloadClientConfigApi = LidarrSystemApis.downloadClientConfig(retrofit),
            notificationApi = LidarrSystemApis.notification(retrofit),
            remotePathMappingApi = LidarrSystemApis.remotePathMapping(retrofit),
            importListApi = LidarrDiscoveryApis.importList(retrofit),
            importListExclusionApi = LidarrDiscoveryApis.importListExclusion(retrofit),
            metadataApi = LidarrSystemApis.metadata(retrofit),
            commandApi = LidarrQueueApis.command(retrofit),
            tagApi = LidarrSystemApis.tag(retrofit),
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
 * remote path mappings, import lists with their exclusions, connect
 * notifications, metadata consumers, the command endpoint for list sync,
 * and tags for pickers. Lidarr has no import list config endpoint, so
 * per-list settings live on the list resources themselves.
 */
class LidarrSettingsConnectApis(
    val indexerApi: IndexerApi,
    val indexerConfigApi: IndexerConfigApi,
    val indexerFlagApi: IndexerFlagApi,
    val downloadClientApi: DownloadClientApi,
    val downloadClientConfigApi: DownloadClientConfigApi,
    val notificationApi: NotificationApi,
    val remotePathMappingApi: RemotePathMappingApi,
    val importListApi: ImportListApi,
    val importListExclusionApi: ImportListExclusionApi,
    val metadataApi: MetadataApi,
    val commandApi: CommandApi,
    val tagApi: TagApi,
)
