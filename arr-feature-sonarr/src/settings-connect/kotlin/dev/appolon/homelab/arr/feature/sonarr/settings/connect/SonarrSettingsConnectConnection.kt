package dev.appolon.homelab.arr.feature.sonarr.settings.connect

import dev.appolon.homelab.arr.api.sonarr.CommandApi
import dev.appolon.homelab.arr.api.sonarr.DownloadClientApi
import dev.appolon.homelab.arr.api.sonarr.DownloadClientConfigApi
import dev.appolon.homelab.arr.api.sonarr.ImportListApi
import dev.appolon.homelab.arr.api.sonarr.ImportListConfigApi
import dev.appolon.homelab.arr.api.sonarr.ImportListExclusionApi
import dev.appolon.homelab.arr.api.sonarr.IndexerApi
import dev.appolon.homelab.arr.api.sonarr.IndexerConfigApi
import dev.appolon.homelab.arr.api.sonarr.IndexerFlagApi
import dev.appolon.homelab.arr.api.sonarr.MetadataApi
import dev.appolon.homelab.arr.api.sonarr.NotificationApi
import dev.appolon.homelab.arr.api.sonarr.RemotePathMappingApi
import dev.appolon.homelab.arr.api.sonarr.SonarrConnectApis
import dev.appolon.homelab.arr.api.sonarr.SonarrDiscoveryApis
import dev.appolon.homelab.arr.api.sonarr.SonarrQueueApis
import dev.appolon.homelab.arr.api.sonarr.SonarrSystemApis
import dev.appolon.homelab.arr.api.sonarr.TagApi
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
 * Everything the connect settings screens need to talk to one Sonarr
 * server, built on the committed client stack. Mirrors the quality
 * connection: one Retrofit over the shared per-server OkHttp client, with
 * the connect, discovery, queue, and system API factories supplying every
 * interface this group uses.
 */
class SonarrSettingsConnectConnection(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {

    /** The per-server API bundle; null when the server or its secrets are gone. */
    suspend fun connect(serverId: ServerId): SonarrSettingsConnectApis? {
        val server = registry.byId(serverId) ?: return null
        val endpoint = endpointOf(server) ?: return null
        val retrofit = Retrofit.Builder()
            .baseUrl(ServerEndpoint.normalizeBaseUrl(server.baseUrl))
            .client(clientFactory.clientFor(endpoint))
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        return SonarrSettingsConnectApis(
            indexerApi = SonarrConnectApis.indexer(retrofit),
            indexerConfigApi = SonarrConnectApis.indexerConfig(retrofit),
            indexerFlagApi = SonarrConnectApis.indexerFlag(retrofit),
            downloadClientApi = SonarrConnectApis.downloadClient(retrofit),
            downloadClientConfigApi = SonarrConnectApis.downloadClientConfig(retrofit),
            notificationApi = SonarrConnectApis.notification(retrofit),
            remotePathMappingApi = SonarrConnectApis.remotePathMapping(retrofit),
            importListApi = SonarrDiscoveryApis.importList(retrofit),
            importListConfigApi = SonarrDiscoveryApis.importListConfig(retrofit),
            importListExclusionApi = SonarrDiscoveryApis.importListExclusion(retrofit),
            metadataApi = SonarrSystemApis.metadata(retrofit),
            commandApi = SonarrQueueApis.command(retrofit),
            tagApi = SonarrSystemApis.tag(retrofit),
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
 * remote path mappings, import lists with their config and exclusions,
 * connect notifications, metadata consumers, the command endpoint for
 * list sync, and tags for pickers.
 */
class SonarrSettingsConnectApis(
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
    val metadataApi: MetadataApi,
    val commandApi: CommandApi,
    val tagApi: TagApi,
)
