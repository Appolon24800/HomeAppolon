package dev.appolon.homelab.arr.feature.bazarr.series

import android.content.Context
import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import dev.appolon.homelab.arr.api.bazarr.BadgesApi
import dev.appolon.homelab.arr.api.bazarr.BazarrLibraryApis
import dev.appolon.homelab.arr.api.bazarr.BazarrSystemApis
import dev.appolon.homelab.arr.api.bazarr.EpisodesApi
import dev.appolon.homelab.arr.api.bazarr.EpisodesBlacklistApi
import dev.appolon.homelab.arr.api.bazarr.MoviesApi
import dev.appolon.homelab.arr.api.bazarr.MoviesBlacklistApi
import dev.appolon.homelab.arr.api.bazarr.SeriesApi
import dev.appolon.homelab.arr.api.bazarr.SystemLanguagesProfilesApi
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.data.ServerRegistry
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
import dev.appolon.homelab.arr.net.ArrClientFactory
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.net.DefaultArrClientFactory
import dev.appolon.homelab.arr.net.ServerEndpoint
import dev.appolon.homelab.arr.net.TrustSpec
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
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
 * Everything the Bazarr list screens need to talk to one server, built on the
 * committed client stack: [DefaultArrClientFactory] honors proxy, basic auth,
 * and trust, and one Retrofit instance serves every Bazarr interface so API
 * calls and poster fetches share a connection pool and credential path.
 */
class BazarrLibraryConnection(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {

    /** The per-server API bundle; null when the server or its secrets are gone. */
    suspend fun connect(serverId: ServerId): BazarrLibraryApiBundle? {
        val server = registry.byId(serverId) ?: return null
        val endpoint = endpointOf(server) ?: return null
        val client = clientFactory.clientFor(endpoint)
        val baseUrl = ServerEndpoint.normalizeBaseUrl(server.baseUrl)
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        return BazarrLibraryApiBundle(
            seriesApi = BazarrLibraryApis.series(retrofit),
            moviesApi = BazarrLibraryApis.movies(retrofit),
            episodesApi = BazarrLibraryApis.episodes(retrofit),
            badgesApi = BazarrLibraryApis.badges(retrofit),
            episodesBlacklistApi = BazarrLibraryApis.episodesBlacklist(retrofit),
            moviesBlacklistApi = BazarrLibraryApis.moviesBlacklist(retrofit),
            languageProfilesApi = BazarrSystemApis.languageProfiles(retrofit),
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

/** Per-server API bundle handed to the repository; every call shares one Retrofit. */
class BazarrLibraryApiBundle(
    val seriesApi: SeriesApi,
    val moviesApi: MoviesApi,
    val episodesApi: EpisodesApi,
    val badgesApi: BadgesApi,
    val episodesBlacklistApi: EpisodesBlacklistApi,
    val moviesBlacklistApi: MoviesBlacklistApi,
    val languageProfilesApi: SystemLanguagesProfilesApi,
    val baseUrl: String,
    val client: OkHttpClient,
) {
    /**
     * Coil loader whose calls ride the same authenticated OkHttp client as
     * the API. Bazarr serves posters from the upstream Sonarr/Radarr remote
     * URLs, so most requests never need the key, but relative paths some
     * servers hand out stay authenticated this way.
     */
    fun imageLoader(context: Context): ImageLoader =
        okhttpImageLoader(context, client)
}

/**
 * Coil network fetcher over the same OkHttp client the API uses, so poster
 * requests carry the server's X-Api-Key header, proxy, and trust settings
 * without a second credential path. coil3 ships no resolvable http fetcher in
 * this environment, so this implements the public [Fetcher] contract
 * directly, mirroring the Radarr feature's loader.
 */
private class OkHttpCoilFetcher private constructor(
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
                OkHttpCoilFetcher(callFactory, url)
            } else {
                null
            }
        }
    }
}

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

/**
 * Coil loader over the shared per-server client, mirroring the Radarr
 * feature: the coil-network-okhttp artifact cannot be resolved in this
 * environment, so an OkHttp [Fetcher][coil3.fetch.Fetcher] is implemented
 * directly over the public contract.
 */
internal fun okhttpImageLoader(context: Context, client: OkHttpClient): ImageLoader =
    ImageLoader.Builder(context)
        .components { add(OkHttpCoilFetcher.Factory(client)) }
        .build()
