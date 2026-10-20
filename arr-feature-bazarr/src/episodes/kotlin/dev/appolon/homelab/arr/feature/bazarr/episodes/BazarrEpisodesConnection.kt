package dev.appolon.homelab.arr.feature.bazarr.episodes

import dev.appolon.homelab.arr.api.bazarr.BazarrLibraryApis
import dev.appolon.homelab.arr.api.bazarr.BazarrSubtitlesApis
import dev.appolon.homelab.arr.api.bazarr.BazarrSystemApis
import dev.appolon.homelab.arr.api.bazarr.EpisodesApi
import dev.appolon.homelab.arr.api.bazarr.EpisodesHistoryApi
import dev.appolon.homelab.arr.api.bazarr.EpisodesSubtitlesApi
import dev.appolon.homelab.arr.api.bazarr.EpisodesWantedApi
import dev.appolon.homelab.arr.api.bazarr.MoviesApi
import dev.appolon.homelab.arr.api.bazarr.MoviesHistoryApi
import dev.appolon.homelab.arr.api.bazarr.MoviesSubtitlesApi
import dev.appolon.homelab.arr.api.bazarr.MoviesWantedApi
import dev.appolon.homelab.arr.api.bazarr.ProvidersEpisodesApi
import dev.appolon.homelab.arr.api.bazarr.ProvidersMoviesApi
import dev.appolon.homelab.arr.api.bazarr.SeriesApi
import dev.appolon.homelab.arr.api.bazarr.SubtitleContentsApi
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
 * Everything the episodes and movie subtitle screens need to talk to one
 * Bazarr server. One [DefaultArrClientFactory] client per server backs both
 * Retrofit services so every call rides the same proxy, auth, and trust path
 * the rest of the app configured for that server.
 */
class BazarrEpisodesConnection(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {

    /** The per-server API bundle; null when the server or its secrets are gone. */
    suspend fun connect(serverId: ServerId): BazarrEpisodesApis? {
        val server = registry.byId(serverId) ?: return null
        val endpoint = endpointOf(server) ?: return null
        val client = clientFactory.clientFor(endpoint)
        val baseUrl = ServerEndpoint.normalizeBaseUrl(server.baseUrl)
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        return BazarrEpisodesApis(
            seriesApi = BazarrLibraryApis.series(retrofit),
            episodesApi = BazarrLibraryApis.episodes(retrofit),
            episodesSubtitlesApi = BazarrLibraryApis.episodesSubtitles(retrofit),
            episodesWantedApi = BazarrLibraryApis.episodesWanted(retrofit),
            episodesHistoryApi = BazarrLibraryApis.episodesHistory(retrofit),
            moviesApi = BazarrLibraryApis.movies(retrofit),
            moviesSubtitlesApi = BazarrLibraryApis.moviesSubtitles(retrofit),
            moviesWantedApi = BazarrLibraryApis.moviesWanted(retrofit),
            moviesHistoryApi = BazarrLibraryApis.moviesHistory(retrofit),
            providersEpisodesApi = BazarrSubtitlesApis.providersEpisodes(retrofit),
            providersMoviesApi = BazarrSubtitlesApis.providersMovies(retrofit),
            subtitleContentsApi = BazarrSubtitlesApis.subtitleContents(retrofit),
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

/** Per-server Retrofit bundle handed to the repository. */
class BazarrEpisodesApis(
    val seriesApi: SeriesApi,
    val episodesApi: EpisodesApi,
    val episodesSubtitlesApi: EpisodesSubtitlesApi,
    val episodesWantedApi: EpisodesWantedApi,
    val episodesHistoryApi: EpisodesHistoryApi,
    val moviesApi: MoviesApi,
    val moviesSubtitlesApi: MoviesSubtitlesApi,
    val moviesWantedApi: MoviesWantedApi,
    val moviesHistoryApi: MoviesHistoryApi,
    val providersEpisodesApi: ProvidersEpisodesApi,
    val providersMoviesApi: ProvidersMoviesApi,
    val subtitleContentsApi: SubtitleContentsApi,
    val languagesApi: SystemLanguagesApi,
)
