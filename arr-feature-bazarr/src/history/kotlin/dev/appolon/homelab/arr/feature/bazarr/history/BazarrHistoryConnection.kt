package dev.appolon.homelab.arr.feature.bazarr.history

import dev.appolon.homelab.arr.api.bazarr.BazarrLibraryApis
import dev.appolon.homelab.arr.api.bazarr.BazarrSystemApis
import dev.appolon.homelab.arr.api.bazarr.EpisodesBlacklistApi
import dev.appolon.homelab.arr.api.bazarr.EpisodesHistoryApi
import dev.appolon.homelab.arr.api.bazarr.MoviesBlacklistApi
import dev.appolon.homelab.arr.api.bazarr.MoviesHistoryApi
import dev.appolon.homelab.arr.api.bazarr.SystemLanguagesApi
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
 * Everything the subtitle history and blacklist screens need to talk to one
 * Bazarr server. One client per server backs every Retrofit service here, so
 * the screens ride the same proxy, auth, and trust path the rest of the app
 * configured for that server.
 */
class BazarrHistoryConnection(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {

    /** The per-server API bundle; null when the server or its secrets are gone. */
    suspend fun connect(serverId: ServerId): BazarrHistoryApis? {
        val server = registry.byId(serverId) ?: return null
        val endpoint = endpointOf(server) ?: return null
        val client = clientFactory.clientFor(endpoint)
        val baseUrl = ServerEndpoint.normalizeBaseUrl(server.baseUrl)
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        return BazarrHistoryApis(
            episodesHistoryApi = BazarrLibraryApis.episodesHistory(retrofit),
            moviesHistoryApi = BazarrLibraryApis.moviesHistory(retrofit),
            episodesBlacklistApi = BazarrLibraryApis.episodesBlacklist(retrofit),
            moviesBlacklistApi = BazarrLibraryApis.moviesBlacklist(retrofit),
            languagesApi = BazarrSystemApis.languages(retrofit),
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

/** Per-server Retrofit bundle handed to the history repository. */
class BazarrHistoryApis(
    val episodesHistoryApi: EpisodesHistoryApi,
    val moviesHistoryApi: MoviesHistoryApi,
    val episodesBlacklistApi: EpisodesBlacklistApi,
    val moviesBlacklistApi: MoviesBlacklistApi,
    val languagesApi: SystemLanguagesApi,
)
