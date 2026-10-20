package dev.appolon.homelab.arr.feature.sonarr.search

import dev.appolon.homelab.arr.api.sonarr.FileSystemApi
import dev.appolon.homelab.arr.api.sonarr.LanguageApi
import dev.appolon.homelab.arr.api.sonarr.ManualImportApi
import dev.appolon.homelab.arr.api.sonarr.QualityProfileApi
import dev.appolon.homelab.arr.api.sonarr.ReleaseApi
import dev.appolon.homelab.arr.api.sonarr.ReleasePushApi
import dev.appolon.homelab.arr.api.sonarr.SeriesApi
import dev.appolon.homelab.arr.api.sonarr.SonarrCalendarApis
import dev.appolon.homelab.arr.api.sonarr.SonarrLibraryApis
import dev.appolon.homelab.arr.api.sonarr.SonarrQueueApis
import dev.appolon.homelab.arr.api.sonarr.SonarrSystemApis
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
 * manual import, filesystem, series, language, and quality profile clients.
 */
data class SonarrSearchApis(
    val releaseApi: ReleaseApi,
    val releasePushApi: ReleasePushApi,
    val manualImportApi: ManualImportApi,
    val fileSystemApi: FileSystemApi,
    val seriesApi: SeriesApi,
    val languageApi: LanguageApi,
    val qualityProfileApi: QualityProfileApi,
)

/**
 * Production connection for the search feature: one cached Retrofit instance
 * per server, built over [DefaultArrClientFactory] so proxy, basic auth, and
 * TLS trust match every other call this app makes. Mirrors the queue group's
 * connection shape.
 */
class SonarrSearchConnection(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {

    private val retrofitCache = HashMap<ServerId, Pair<String, Retrofit>>()

    /** Null when the server is unknown or its stored key cannot be decrypted. */
    suspend fun apis(serverId: ServerId): SonarrSearchApis? =
        retrofitFor(serverId)?.let { retrofit ->
            SonarrSearchApis(
                releaseApi = SonarrQueueApis.release(retrofit),
                releasePushApi = SonarrQueueApis.releasePush(retrofit),
                manualImportApi = SonarrQueueApis.manualImport(retrofit),
                fileSystemApi = SonarrSystemApis.fileSystem(retrofit),
                seriesApi = SonarrLibraryApis.series(retrofit),
                languageApi = SonarrCalendarApis.language(retrofit),
                qualityProfileApi = SonarrCalendarApis.qualityProfile(retrofit),
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
