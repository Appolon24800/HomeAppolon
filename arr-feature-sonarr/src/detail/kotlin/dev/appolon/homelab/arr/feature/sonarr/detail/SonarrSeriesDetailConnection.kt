package dev.appolon.homelab.arr.feature.sonarr.detail

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dev.appolon.homelab.arr.api.sonarr.EpisodeApi
import dev.appolon.homelab.arr.api.sonarr.EpisodeFileApi
import dev.appolon.homelab.arr.api.sonarr.HistoryApi
import dev.appolon.homelab.arr.api.sonarr.LanguageApi
import dev.appolon.homelab.arr.api.sonarr.ManualImportApi
import dev.appolon.homelab.arr.api.sonarr.QualityDefinitionApi
import dev.appolon.homelab.arr.api.sonarr.QualityProfileApi
import dev.appolon.homelab.arr.api.sonarr.ReleaseApi
import dev.appolon.homelab.arr.api.sonarr.RenameEpisodeApi
import dev.appolon.homelab.arr.api.sonarr.SeasonPassApi
import dev.appolon.homelab.arr.api.sonarr.SeriesApi
import dev.appolon.homelab.arr.api.sonarr.SonarrLibraryApis
import dev.appolon.homelab.arr.api.sonarr.SonarrQueueApis
import dev.appolon.homelab.arr.api.sonarr.SonarrSystemApis
import dev.appolon.homelab.arr.api.sonarr.model.LanguageResource
import dev.appolon.homelab.arr.api.sonarr.model.QualityDefinitionResource
import dev.appolon.homelab.arr.api.sonarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.data.ServerRegistry
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
import dev.appolon.homelab.arr.feature.sonarr.detail.data.SonarrCommandPoster
import dev.appolon.homelab.arr.net.ArrClientFactory
import dev.appolon.homelab.arr.net.ArrCommandRunner
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.net.DefaultArrClientFactory
import dev.appolon.homelab.arr.net.ServerEndpoint
import dev.appolon.homelab.arr.net.TrustSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Everything the series detail screens need to talk to one Sonarr server.
 * Built exactly like the library connection: one [DefaultArrClientFactory]
 * client per server shared by Retrofit and Coil so API calls, commands, and
 * cover fetches ride the same proxy, auth, and trust path.
 */
class SonarrSeriesDetailConnection(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {

    /** The per-server API bundle; null when the server or its secrets are gone. */
    suspend fun connect(serverId: ServerId): SonarrSeriesDetailApis? {
        val server = registry.byId(serverId) ?: return null
        val endpoint = endpointOf(server) ?: return null
        val client = clientFactory.clientFor(endpoint)
        val baseUrl = ServerEndpoint.normalizeBaseUrl(server.baseUrl)
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        val qualityDefinitionApi = retrofit.create(QualityDefinitionApi::class.java)
        val languageApi = retrofit.create(LanguageApi::class.java)
        val qualityProfileApi = retrofit.create(QualityProfileApi::class.java)
        val tagApi = SonarrSystemApis.tag(retrofit)
        return SonarrSeriesDetailApis(
            seriesApi = SonarrLibraryApis.series(retrofit),
            episodeApi = SonarrLibraryApis.episode(retrofit),
            episodeFileApi = SonarrLibraryApis.episodeFile(retrofit),
            renameEpisodeApi = SonarrLibraryApis.renameEpisode(retrofit),
            seasonPassApi = SonarrLibraryApis.seasonPass(retrofit),
            historyApi = SonarrQueueApis.history(retrofit),
            releaseApi = SonarrQueueApis.release(retrofit),
            manualImportApi = SonarrQueueApis.manualImport(retrofit),
            qualityProfiles = { qualityProfileApi.list() },
            qualityDefinitions = { qualityDefinitionApi.list() },
            languages = { languageApi.list() },
            tags = { tagApi.list() },
            commandPoster = OkHttpSonarrCommandPoster(client, baseUrl),
            baseUrl = baseUrl,
            client = client,
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
 * Per-server API bundle handed to the detail repository. The lookup lambdas
 * stay lazy so the repository controls fetch timing and caching.
 */
class SonarrSeriesDetailApis(
    val seriesApi: SeriesApi,
    val episodeApi: EpisodeApi,
    val episodeFileApi: EpisodeFileApi,
    val renameEpisodeApi: RenameEpisodeApi,
    val seasonPassApi: SeasonPassApi,
    val historyApi: HistoryApi,
    val releaseApi: ReleaseApi,
    val manualImportApi: ManualImportApi,
    val qualityProfiles: suspend () -> List<QualityProfileResource>,
    val qualityDefinitions: suspend () -> List<QualityDefinitionResource>,
    val languages: suspend () -> List<LanguageResource>,
    val tags: suspend () -> List<TagResource>,
    val commandPoster: SonarrCommandPoster,
    val baseUrl: String,
    val client: OkHttpClient,
) {
    /**
     * Coil loader on the authenticated client, required for local MediaCover
     * banner paths. One loader per connection; Coil deduplicates in flight.
     */
    fun imageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(client)) }
        .crossfade(true)
        .build()
}

/**
 * Posts a raw command body to `/api/v3/command`. Detail commands carry
 * `episodeIds` or `seasonNumber` at the JSON root, a shape the committed
 * `CommandResource` DTO cannot express, so the body travels as JSON text
 * over the same client the rest of the connection uses.
 */
class OkHttpSonarrCommandPoster(
    client: OkHttpClient,
    baseUrl: String,
) : SonarrCommandPoster {

    private val runner = ArrCommandRunner(client, baseUrl)

    override suspend fun post(bodyJson: String): ArrResult<Unit> =
        withContext(Dispatchers.IO) { runner.post("/api/v3/command", bodyJson) }
}
