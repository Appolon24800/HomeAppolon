package dev.appolon.homelab.arr.feature.radarr.search

import dev.appolon.homelab.arr.api.radarr.ManualImportApi
import dev.appolon.homelab.arr.api.radarr.RadarrApis
import dev.appolon.homelab.arr.api.radarr.RadarrCalendarApis
import dev.appolon.homelab.arr.api.radarr.RadarrQueueApis
import dev.appolon.homelab.arr.api.radarr.RadarrSystemApis
import dev.appolon.homelab.arr.api.radarr.api.MovieApi
import dev.appolon.homelab.arr.api.radarr.model.LanguageResource
import dev.appolon.homelab.arr.api.radarr.model.QualityProfileResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.data.ServerRegistry
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
import dev.appolon.homelab.arr.feature.radarr.search.data.RadarrReleaseApi
import dev.appolon.homelab.arr.feature.radarr.search.data.RadarrReleasePushApi
import dev.appolon.homelab.arr.net.ArrClientFactory
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.net.DefaultArrClientFactory
import dev.appolon.homelab.arr.net.ServerEndpoint
import dev.appolon.homelab.arr.net.TrustSpec
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Per-server API bundle for the search group: the feature-owned release
 * interfaces plus the committed manual import, filesystem, language, movie,
 * and quality profile clients.
 */
data class RadarrSearchApis(
    val releaseApi: RadarrReleaseApi,
    val releasePushApi: RadarrReleasePushApi,
    val manualImportApi: ManualImportApi,
    val fileSystemApi: dev.appolon.homelab.arr.api.radarr.FileSystemApi,
    val movieApi: MovieApi,
    val languageApi: dev.appolon.homelab.arr.api.radarr.LanguageApi,
    val qualityProfileApi: dev.appolon.homelab.arr.api.radarr.QualityProfileApi,
) {
    internal suspend fun languageTable(): List<LanguageResource> = languageApi.languages()

    internal suspend fun qualityProfile(id: Int): QualityProfileResource? =
        qualityProfileApi.qualityProfile(id).body()
}

/**
 * Production connection for the search feature: one cached Retrofit instance
 * per server, built over [DefaultArrClientFactory] so proxy, basic auth, and
 * TLS trust match every other call this app makes. Mirrors the detail
 * group's connection shape.
 */
class RadarrSearchConnection(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {

    private val retrofitCache = HashMap<ServerId, Pair<String, Retrofit>>()

    /** Null when the server is unknown or its stored key cannot be decrypted. */
    suspend fun apis(serverId: ServerId): RadarrSearchApis? =
        retrofitFor(serverId)?.let { retrofit ->
            RadarrSearchApis(
                releaseApi = retrofit.create(RadarrReleaseApi::class.java),
                releasePushApi = retrofit.create(RadarrReleasePushApi::class.java),
                manualImportApi = RadarrQueueApis.manualImport(retrofit),
                fileSystemApi = RadarrSystemApis.fileSystem(retrofit),
                movieApi = RadarrApis.movie(retrofit),
                languageApi = RadarrCalendarApis.language(retrofit),
                qualityProfileApi = RadarrCalendarApis.qualityProfile(retrofit),
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
