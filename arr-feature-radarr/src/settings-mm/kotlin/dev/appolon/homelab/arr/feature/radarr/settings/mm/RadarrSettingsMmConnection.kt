package dev.appolon.homelab.arr.feature.radarr.settings.mm

import dev.appolon.homelab.arr.api.radarr.DiskSpaceApi
import dev.appolon.homelab.arr.api.radarr.FileSystemApi
import dev.appolon.homelab.arr.api.radarr.MediaManagementConfigApi
import dev.appolon.homelab.arr.api.radarr.NamingConfigApi
import dev.appolon.homelab.arr.api.radarr.RadarrSystemApis
import dev.appolon.homelab.arr.api.radarr.RootFolderApi
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
 * Everything the media management screens need to talk to one Radarr
 * server, built on the committed client stack. Mirrors the calendar and
 * add-new connections: one Retrofit over the shared per-server OkHttp
 * client, with the system API factories supplying every interface.
 */
class RadarrSettingsMmConnection(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {

    /** The per-server API bundle; null when the server or its secrets are gone. */
    suspend fun connect(serverId: ServerId): RadarrSettingsMmApis? {
        val server = registry.byId(serverId) ?: return null
        val endpoint = endpointOf(server) ?: return null
        val retrofit = Retrofit.Builder()
            .baseUrl(ServerEndpoint.normalizeBaseUrl(server.baseUrl))
            .client(clientFactory.clientFor(endpoint))
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        return RadarrSettingsMmApis(
            mediaManagementConfigApi = RadarrSystemApis.mediaManagementConfig(retrofit),
            namingConfigApi = RadarrSystemApis.namingConfig(retrofit),
            rootFolderApi = RadarrSystemApis.rootFolder(retrofit),
            fileSystemApi = RadarrSystemApis.fileSystem(retrofit),
            diskSpaceApi = RadarrSystemApis.diskSpace(retrofit),
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
 * Per-server API bundle handed to the repositories of this feature: the
 * two config endpoints, root folders, the filesystem browser, and disk
 * space for the free-space bars.
 */
class RadarrSettingsMmApis(
    val mediaManagementConfigApi: MediaManagementConfigApi,
    val namingConfigApi: NamingConfigApi,
    val rootFolderApi: RootFolderApi,
    val fileSystemApi: FileSystemApi,
    val diskSpaceApi: DiskSpaceApi,
)
