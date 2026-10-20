package dev.appolon.homelab.arr.feature.lidarr.search

import dev.appolon.homelab.arr.api.lidarr.AlbumApi
import dev.appolon.homelab.arr.api.lidarr.ArtistApi
import dev.appolon.homelab.arr.api.lidarr.FileSystemApi
import dev.appolon.homelab.arr.api.lidarr.LidarrCalendarApis
import dev.appolon.homelab.arr.api.lidarr.LidarrLibraryApis
import dev.appolon.homelab.arr.api.lidarr.LidarrQueueApis
import dev.appolon.homelab.arr.api.lidarr.LidarrSystemApis
import dev.appolon.homelab.arr.api.lidarr.ManualImportApi
import dev.appolon.homelab.arr.api.lidarr.QualityProfileApi
import dev.appolon.homelab.arr.api.lidarr.ReleaseApi
import dev.appolon.homelab.arr.api.lidarr.ReleasePushApi
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
 * Per-server API bundle for the search group: the committed release, push,
 * manual import, filesystem, artist, album, and quality profile clients.
 */
data class LidarrSearchApis(
    val releaseApi: ReleaseApi,
    val releasePushApi: ReleasePushApi,
    val manualImportApi: ManualImportApi,
    val fileSystemApi: FileSystemApi,
    val artistApi: ArtistApi,
    val albumApi: AlbumApi,
    val qualityProfileApi: QualityProfileApi,
)

/**
 * Production connection for the search feature: one cached Retrofit instance
 * per server, built over [DefaultArrClientFactory] so proxy, basic auth, and
 * TLS trust match every other call this app makes. Mirrors the Sonarr search
 * group's connection shape.
 */
class LidarrSearchConnection(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {

    private val retrofitCache = HashMap<ServerId, Pair<String, Retrofit>>()

    /** Null when the server is unknown or its stored key cannot be decrypted. */
    suspend fun apis(serverId: ServerId): LidarrSearchApis? =
        retrofitFor(serverId)?.let { retrofit ->
            LidarrSearchApis(
                releaseApi = LidarrQueueApis.release(retrofit),
                releasePushApi = LidarrQueueApis.releasePush(retrofit),
                manualImportApi = LidarrQueueApis.manualImport(retrofit),
                fileSystemApi = LidarrSystemApis.fileSystem(retrofit),
                artistApi = LidarrLibraryApis.artist(retrofit),
                albumApi = LidarrLibraryApis.album(retrofit),
                qualityProfileApi = LidarrCalendarApis.qualityProfile(retrofit),
            )
        }

    private suspend fun serverOf(serverId: ServerId): ArrServer? = registry.byId(serverId)

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

    private suspend fun retrofitFor(serverId: ServerId): Retrofit? {
        val server = serverOf(serverId) ?: return null
        val cached = synchronized(retrofitCache) { retrofitCache[serverId] }
        if (cached != null && cached.first == server.baseUrl) return cached.second
        val endpoint = endpointOf(server) ?: return null
        val retrofit = Retrofit.Builder()
            .baseUrl(ServerEndpoint.normalizeBaseUrl(server.baseUrl))
            .client(clientFactory.clientFor(endpoint))
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        synchronized(retrofitCache) { retrofitCache[serverId] = server.baseUrl to retrofit }
        return retrofit
    }
}
