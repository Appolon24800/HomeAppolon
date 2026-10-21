package dev.appolon.homelab.arr.feature.radarr.addnew

import android.content.Context
import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.request.crossfade
import dev.appolon.homelab.arr.api.radarr.CollectionApi
import dev.appolon.homelab.arr.api.radarr.ImportListMoviesApi
import dev.appolon.homelab.arr.api.radarr.QualityProfileApi
import dev.appolon.homelab.arr.api.radarr.RadarrApis
import dev.appolon.homelab.arr.api.radarr.RadarrCalendarApis
import dev.appolon.homelab.arr.api.radarr.RadarrImportlistApis
import dev.appolon.homelab.arr.api.radarr.RadarrSystemApis
import dev.appolon.homelab.arr.api.radarr.RootFolderApi
import dev.appolon.homelab.arr.api.radarr.TagApi
import dev.appolon.homelab.arr.api.radarr.api.MovieApi
import dev.appolon.homelab.arr.api.radarr.api.MovieImportApi
import dev.appolon.homelab.arr.api.radarr.api.MovieLookupApi
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.data.ServerRegistry
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
import dev.appolon.homelab.arr.feature.radarr.addnew.data.AddNewCommandRunner
import dev.appolon.homelab.arr.net.ArrClientFactory
import dev.appolon.homelab.arr.net.ArrCommandRunner
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.net.DefaultArrClientFactory
import dev.appolon.homelab.arr.net.ServerEndpoint
import dev.appolon.homelab.arr.net.TrustSpec
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.FileSystem
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Everything the Add new screens need to talk to one Radarr server, built on
 * the committed client stack: [DefaultArrClientFactory] honors proxy, basic
 * auth, and trust, and the Retrofit instance reuses that client so API calls
 * and poster fetches share one connection pool and credential path.
 */
class RadarrAddNewConnection(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {

    /** The per-server API bundle; null when the server or its secrets are gone. */
    suspend fun connect(serverId: ServerId): RadarrAddNewApis? {
        val server = registry.byId(serverId) ?: return null
        val endpoint = endpointOf(server) ?: return null
        val client = clientFactory.clientFor(endpoint)
        val baseUrl = ServerEndpoint.normalizeBaseUrl(server.baseUrl)
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        return RadarrAddNewApis(
            movieLookupApi = RadarrApis.movieLookup(retrofit),
            movieApi = RadarrApis.movie(retrofit),
            movieImportApi = RadarrApis.movieImport(retrofit),
            importListMoviesApi = RadarrImportlistApis.importListMovies(retrofit),
            collectionApi = RadarrImportlistApis.collection(retrofit),
            rootFolderApi = RadarrSystemApis.rootFolder(retrofit),
            tagApi = RadarrSystemApis.tag(retrofit),
            qualityProfileApi = RadarrCalendarApis.qualityProfile(retrofit),
            commandRunner = OkHttpAddNewCommandRunner(client, baseUrl),
            baseUrl = baseUrl,
            client = client,
            imageClient = clientFactory.imageClientFor(endpoint),
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
 * Per-server API bundle handed to the repositories of this feature. The
 * factories live in `:arr-api-radarr`; the connection owns nothing but wiring.
 */
class RadarrAddNewApis(
    val movieLookupApi: MovieLookupApi,
    val movieApi: MovieApi,
    val movieImportApi: MovieImportApi,
    val importListMoviesApi: ImportListMoviesApi,
    val collectionApi: CollectionApi,
    val rootFolderApi: RootFolderApi,
    val tagApi: TagApi,
    val qualityProfileApi: QualityProfileApi,
    val commandRunner: AddNewCommandRunner,
    val baseUrl: String,
    val client: OkHttpClient,
    /** Credential-free client for remote artwork; shares proxy and trust with [client]. */
    val imageClient: OkHttpClient,
) {
    /**
     * Coil loader for lookup, discovery, and collection posters. Every URL
     * it sees is remote TMDB art, so it rides the credential-free image
     * client and the API key never travels to a third-party host. One
     * instance per screen is enough; Coil deduplicates requests in flight.
     */
    fun imageLoader(context: Context): ImageLoader = addNewImageLoader(context, imageClient)
}

/**
 * Runs Radarr commands over a raw POST instead of the typed `CommandApi`:
 * command payloads put selection fields such as `movieIds` at the JSON root,
 * a shape the committed `CommandResource` DTO cannot express. The body is
 * built here; the wire path, auth, and prefix handling are [ArrCommandRunner].
 */
class OkHttpAddNewCommandRunner(
    client: OkHttpClient,
    baseUrl: String,
) : AddNewCommandRunner {

    private val runner = ArrCommandRunner(client, baseUrl)

    override suspend fun run(command: String, movieIds: List<Int>?): ArrResult<Unit> {
        val body = buildJsonObject {
            put("name", command)
            movieIds?.takeIf { it.isNotEmpty() }?.let { ids ->
                put("movieIds", JsonArray(ids.map { JsonPrimitive(it) }))
            }
        }.toString()
        return withContext(Dispatchers.IO) { runner.post("/api/v3/command", body) }
    }
}

/**
 * Coil network fetcher over the same OkHttp client the API uses, so poster
 * requests carry the server's X-Api-Key header, proxy, and trust settings
 * without a second credential path. The catalog's coil-network-okhttp
 * artifact cannot be resolved in this environment, and coil3 ships no built
 * in http fetcher, so this implements the public [Fetcher] contract directly
 * (same approach as the library feature's fetcher, owned separately here
 * because source sets compile independently).
 */
class AddNewPosterFetcher private constructor(
    private val callFactory: Call.Factory,
    private val url: String,
) : Fetcher {

    override suspend fun fetch(): SourceFetchResult {
        val request = try {
            Request.Builder().url(url).build()
        } catch (t: Throwable) {
            throw IOException("Unusable image URL: $url", t)
        }
        val response = callFactory.newCall(request).await()
        val body = response.body
        if (!response.isSuccessful || body == null) {
            response.close()
            throw IOException("HTTP ${response.code} loading $url")
        }
        return SourceFetchResult(
            source = ImageSource(body.source(), FileSystem.SYSTEM),
            mimeType = body.contentType()?.toString(),
            dataSource = DataSource.NETWORK,
        )
    }

    /** Registered for [Uri] because Coil maps String models through StringMapper first. */
    class Factory(
        private val callFactory: Call.Factory,
    ) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val url = data.toString()
            return if (url.startsWith("http", ignoreCase = true)) {
                AddNewPosterFetcher(callFactory, url)
            } else {
                null
            }
        }
    }
}

/** Builds the loader for one server connection; needs the Android context on this platform. */
fun addNewImageLoader(context: Context, client: OkHttpClient): ImageLoader =
    ImageLoader.Builder(context)
        .components { add(AddNewPosterFetcher.Factory(client)) }
        .crossfade(true)
        .build()

/** Suspends until the call answers; cancelling the coroutine cancels the call. */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response)
        }

        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }
    })
    continuation.invokeOnCancellation { runCatching { cancel() } }
}
