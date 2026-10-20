package dev.appolon.homelab.arr.feature.lidarr.library

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dev.appolon.homelab.arr.api.lidarr.AlbumApi
import dev.appolon.homelab.arr.api.lidarr.AlbumStudioApi
import dev.appolon.homelab.arr.api.lidarr.ArtistApi
import dev.appolon.homelab.arr.api.lidarr.ArtistEditorApi
import dev.appolon.homelab.arr.api.lidarr.LidarrCalendarApis
import dev.appolon.homelab.arr.api.lidarr.LidarrLibraryApis
import dev.appolon.homelab.arr.api.lidarr.MetadataProfileApi
import dev.appolon.homelab.arr.api.lidarr.model.MetadataProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileResource
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.data.ServerRegistry
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
import dev.appolon.homelab.arr.feature.lidarr.library.data.ArtistCommandRunner
import dev.appolon.homelab.arr.net.ArrClientFactory
import dev.appolon.homelab.arr.net.ArrCommandRunner
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.net.DefaultArrClientFactory
import dev.appolon.homelab.arr.net.ServerEndpoint
import dev.appolon.homelab.arr.net.TrustSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Everything the library screen needs to talk to one Lidarr server, built on
 * the committed client stack: [DefaultArrClientFactory] honors proxy, basic
 * auth, and trust, and the Retrofit instance reuses that client so API calls
 * and cover fetches share one connection pool and credential path.
 */
class LidarrLibraryConnection(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {

    /** The per-server API bundle; null when the server or its secrets are gone. */
    suspend fun connect(serverId: ServerId): LidarrLibraryClient? {
        val server = registry.byId(serverId) ?: return null
        val endpoint = endpointOf(server) ?: return null
        val client = clientFactory.clientFor(endpoint)
        val baseUrl = ServerEndpoint.normalizeBaseUrl(server.baseUrl)
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        return LidarrLibraryClient(
            artistApi = LidarrLibraryApis.artist(retrofit),
            artistEditorApi = LidarrLibraryApis.artistEditor(retrofit),
            albumApi = LidarrLibraryApis.album(retrofit),
            albumStudioApi = LidarrLibraryApis.albumStudio(retrofit),
            qualityProfiles = { LidarrCalendarApis.qualityProfile(retrofit).getQualityProfiles() },
            metadataProfiles = {
                retrofit.create(MetadataProfileApi::class.java).getMetadataProfiles()
            },
            commandRunner = OkHttpArtistCommandRunner(client, baseUrl),
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

/** Per-server API bundle handed to the repository. */
class LidarrLibraryClient(
    val artistApi: ArtistApi,
    val artistEditorApi: ArtistEditorApi,
    val albumApi: AlbumApi,
    val albumStudioApi: AlbumStudioApi,
    val qualityProfiles: suspend () -> List<QualityProfileResource>,
    val metadataProfiles: suspend () -> List<MetadataProfileResource>,
    val commandRunner: ArtistCommandRunner,
    val baseUrl: String,
    val client: OkHttpClient,
) {
    /**
     * Coil loader whose calls ride the same authenticated OkHttp client as
     * the API, required for local MediaCover cover paths. One instance per
     * screen is enough; Coil deduplicates requests in flight.
     */
    fun imageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(client)) }
        .build()
}

/**
 * Runs Lidarr commands over a raw POST instead of the Retrofit `CommandApi`:
 * command payloads put selection fields such as `artistIds` at the JSON root,
 * a shape the committed `CommandResource` DTO cannot express, and Lidarr
 * rejects ids nested inside `body`. The body is built here; the wire path,
 * auth, and prefix handling are [ArrCommandRunner].
 */
class OkHttpArtistCommandRunner(
    client: OkHttpClient,
    baseUrl: String,
) : ArtistCommandRunner {

    private val runner = ArrCommandRunner(client, baseUrl)

    override suspend fun run(command: String, artistIds: List<Int>?): ArrResult<Unit> {
        val body = buildJsonObject {
            put("name", command)
            artistIds?.takeIf { it.isNotEmpty() }?.let { ids ->
                put("artistIds", JsonArray(ids.map { JsonPrimitive(it) }))
            }
        }.toString()
        return withContext(Dispatchers.IO) { runner.post("/api/v1/command", body) }
    }
}
