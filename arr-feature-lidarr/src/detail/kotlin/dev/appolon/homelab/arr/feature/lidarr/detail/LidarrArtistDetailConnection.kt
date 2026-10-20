package dev.appolon.homelab.arr.feature.lidarr.detail

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dev.appolon.homelab.arr.api.lidarr.AlbumApi
import dev.appolon.homelab.arr.api.lidarr.ArtistApi
import dev.appolon.homelab.arr.api.lidarr.HistoryApi
import dev.appolon.homelab.arr.api.lidarr.LidarrCalendarApis
import dev.appolon.homelab.arr.api.lidarr.LidarrLibraryApis
import dev.appolon.homelab.arr.api.lidarr.LidarrQueueApis
import dev.appolon.homelab.arr.api.lidarr.ManualImportApi
import dev.appolon.homelab.arr.api.lidarr.ReleaseApi
import dev.appolon.homelab.arr.api.lidarr.RenameTrackApi
import dev.appolon.homelab.arr.api.lidarr.RetagTrackApi
import dev.appolon.homelab.arr.api.lidarr.TagApi
import dev.appolon.homelab.arr.api.lidarr.TrackApi
import dev.appolon.homelab.arr.api.lidarr.TrackFileApi
import dev.appolon.homelab.arr.api.lidarr.model.MetadataProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.QualityDefinitionResource
import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.data.ServerRegistry
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
import dev.appolon.homelab.arr.net.ArrClientFactory
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.net.DefaultArrClientFactory
import dev.appolon.homelab.arr.net.ServerEndpoint
import dev.appolon.homelab.arr.net.TrustSpec
import dev.appolon.homelab.arr.net.arrError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Everything the artist detail screens need to talk to one Lidarr server.
 * Built exactly like the library connection: one [DefaultArrClientFactory]
 * client per server shared by Retrofit and Coil so API calls, commands, and
 * cover fetches ride the same proxy, auth, and trust path.
 */
class LidarrArtistDetailConnection(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {

    /** The per-server API bundle; null when the server or its secrets are gone. */
    suspend fun connect(serverId: ServerId): LidarrArtistDetailApis? {
        val server = registry.byId(serverId) ?: return null
        val endpoint = endpointOf(server) ?: return null
        val client = clientFactory.clientFor(endpoint)
        val baseUrl = ServerEndpoint.normalizeBaseUrl(server.baseUrl)
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        val qualityDefinitionApi = LidarrCalendarApis.qualityDefinition(retrofit)
        val tagApi = retrofit.create(TagApi::class.java)
        return LidarrArtistDetailApis(
            artistApi = LidarrLibraryApis.artist(retrofit),
            albumApi = LidarrLibraryApis.album(retrofit),
            trackApi = LidarrLibraryApis.track(retrofit),
            trackFileApi = LidarrLibraryApis.trackFile(retrofit),
            renameTrackApi = LidarrLibraryApis.renameTrack(retrofit),
            retagTrackApi = LidarrLibraryApis.retagTrack(retrofit),
            historyApi = LidarrQueueApis.history(retrofit),
            releaseApi = LidarrQueueApis.release(retrofit),
            manualImportApi = LidarrQueueApis.manualImport(retrofit),
            qualityProfiles = { LidarrCalendarApis.qualityProfile(retrofit).getQualityProfiles() },
            qualityDefinitions = { qualityDefinitionApi.getQualityDefinitions() },
            metadataProfiles = {
                LidarrCalendarApis.metadataProfile(retrofit).getMetadataProfiles()
            },
            tags = { tagApi.getTags() },
            commandPoster = OkHttpLidarrCommandPoster(client, baseUrl),
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
class LidarrArtistDetailApis(
    val artistApi: ArtistApi,
    val albumApi: AlbumApi,
    val trackApi: TrackApi,
    val trackFileApi: TrackFileApi,
    val renameTrackApi: RenameTrackApi,
    val retagTrackApi: RetagTrackApi,
    val historyApi: HistoryApi,
    val releaseApi: ReleaseApi,
    val manualImportApi: ManualImportApi,
    val qualityProfiles: suspend () -> List<QualityProfileResource>,
    val qualityDefinitions: suspend () -> List<QualityDefinitionResource>,
    val metadataProfiles: suspend () -> List<MetadataProfileResource>,
    val tags: suspend () -> List<TagResource>,
    val commandPoster: LidarrCommandPoster,
    val baseUrl: String,
    val client: OkHttpClient,
) {
    /**
     * Coil loader on the authenticated client, required for local MediaCover
     * banner and album cover paths. One loader per connection; Coil
     * deduplicates in flight.
     */
    fun imageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(client)) }
        .build()
}

/**
 * Posts one raw command body to `/api/v1/command`. Detail commands carry
 * `artistId`, `albumIds`, or `files` at the JSON root, a shape the committed
 * `CommandResource` DTO cannot express, so the body travels as JSON text
 * over the same client the rest of the connection uses.
 */
fun interface LidarrCommandPoster {
    suspend fun post(bodyJson: String): ArrResult<Unit>
}

/** [LidarrCommandPoster] over the shared OkHttp client. */
class OkHttpLidarrCommandPoster(
    private val client: OkHttpClient,
    private val baseUrl: String,
) : LidarrCommandPoster {

    override suspend fun post(bodyJson: String): ArrResult<Unit> = withContext(Dispatchers.IO) {
        val request = try {
            Request.Builder()
                .url(baseUrl.trimEnd('/') + "/api/v1/command")
                .post(bodyJson.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()
        } catch (t: Throwable) {
            return@withContext ArrResult.err(t.arrError())
        }
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    ArrResult.ok(Unit)
                } else {
                    ArrResult.err(response.arrError())
                }
            }
        } catch (t: Throwable) {
            ArrResult.err(t.arrError())
        }
    }
}
