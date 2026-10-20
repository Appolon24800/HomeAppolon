package dev.appolon.homelab.arr.feature.bazarr.wanted

import dev.appolon.homelab.arr.api.bazarr.BadgesApi
import dev.appolon.homelab.arr.api.bazarr.BazarrLibraryApis
import dev.appolon.homelab.arr.api.bazarr.BazarrSystemApis
import dev.appolon.homelab.arr.api.bazarr.EpisodesSubtitlesApi
import dev.appolon.homelab.arr.api.bazarr.EpisodesWantedApi
import dev.appolon.homelab.arr.api.bazarr.MoviesSubtitlesApi
import dev.appolon.homelab.arr.api.bazarr.MoviesWantedApi
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
 * Everything the wanted subtitles screens need to talk to one Bazarr server.
 * One client per server backs every Retrofit service here, so the screens
 * ride the same proxy, auth, and trust path the rest of the app configured
 * for that server.
 */
class BazarrWantedConnection(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {

    /** The per-server API bundle; null when the server or its secrets are gone. */
    suspend fun connect(serverId: ServerId): BazarrWantedApis? {
        val server = registry.byId(serverId) ?: return null
        val endpoint = endpointOf(server) ?: return null
        val client = clientFactory.clientFor(endpoint)
        val baseUrl = ServerEndpoint.normalizeBaseUrl(server.baseUrl)
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        return BazarrWantedApis(
            episodesWantedApi = BazarrLibraryApis.episodesWanted(retrofit),
            moviesWantedApi = BazarrLibraryApis.moviesWanted(retrofit),
            episodesSubtitlesApi = BazarrLibraryApis.episodesSubtitles(retrofit),
            moviesSubtitlesApi = BazarrLibraryApis.moviesSubtitles(retrofit),
            badgesApi = BazarrLibraryApis.badges(retrofit),
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

/** Per-server Retrofit bundle handed to the wanted repository. */
class BazarrWantedApis(
    val episodesWantedApi: EpisodesWantedApi,
    val moviesWantedApi: MoviesWantedApi,
    val episodesSubtitlesApi: EpisodesSubtitlesApi,
    val moviesSubtitlesApi: MoviesSubtitlesApi,
    val badgesApi: BadgesApi,
    val languagesApi: SystemLanguagesApi,
)
