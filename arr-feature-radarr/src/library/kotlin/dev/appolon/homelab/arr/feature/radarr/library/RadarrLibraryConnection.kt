package dev.appolon.homelab.arr.feature.radarr.library

import android.content.Context
import coil3.ImageLoader
import dev.appolon.homelab.arr.api.radarr.RadarrApis
import dev.appolon.homelab.arr.api.radarr.RadarrCalendarApis
import dev.appolon.homelab.arr.api.radarr.api.MovieApi
import dev.appolon.homelab.arr.api.radarr.api.MovieEditorApi
import dev.appolon.homelab.arr.api.radarr.model.QualityProfileResource
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.data.ServerRegistry
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
import dev.appolon.homelab.arr.feature.radarr.library.data.MovieCommandRunner
import dev.appolon.homelab.arr.net.ArrClientFactory
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.net.DefaultArrClientFactory
import dev.appolon.homelab.arr.net.ServerEndpoint
import dev.appolon.homelab.arr.net.TrustSpec
import dev.appolon.homelab.arr.net.arrError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Everything the library screen needs to talk to one Radarr server, built on
 * the committed client stack: [DefaultArrClientFactory] honors proxy, basic
 * auth, and trust, and the Retrofit instance reuses that client so API calls
 * and poster fetches share one connection pool and credential path.
 */
class RadarrLibraryConnection(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {

    /** The per-server API bundle; null when the server or its secrets are gone. */
    suspend fun connect(serverId: ServerId): RadarrLibraryApis? {
        val server = registry.byId(serverId) ?: return null
        val endpoint = endpointOf(server) ?: return null
        val client = clientFactory.clientFor(endpoint)
        val baseUrl = ServerEndpoint.normalizeBaseUrl(server.baseUrl)
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        return RadarrLibraryApis(
            movieApi = RadarrApis.movie(retrofit),
            movieEditorApi = RadarrApis.movieEditor(retrofit),
            qualityProfiles = { RadarrCalendarApis.qualityProfile(retrofit).qualityProfiles() },
            commandRunner = OkHttpCommandRunner(client, baseUrl),
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
class RadarrLibraryApis(
    val movieApi: MovieApi,
    val movieEditorApi: MovieEditorApi,
    val qualityProfiles: suspend () -> List<QualityProfileResource>,
    val commandRunner: MovieCommandRunner,
    val baseUrl: String,
    val client: OkHttpClient,
) {
    /**
     * Coil loader whose calls ride the same authenticated OkHttp client as
     * the API, required for local MediaCover poster paths. One instance per
     * screen is enough; Coil deduplicates requests in flight.
     */
    fun imageLoader(context: Context): ImageLoader = okhttpImageLoader(context, client)
}

/**
 * Runs Radarr commands over a raw POST instead of the Retrofit `CommandApi`:
 * command payloads put selection fields such as `movieIds` at the JSON root,
 * a shape the committed `CommandResource` DTO cannot express, and Radarr
 * rejects ids nested inside `body`. Wire path, auth, and prefix handling are
 * identical to every other call.
 */
class OkHttpCommandRunner(
    private val client: OkHttpClient,
    private val baseUrl: String,
) : MovieCommandRunner {

    override suspend fun run(command: String, movieIds: List<Int>?): ArrResult<Unit> =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("name", command)
                movieIds?.takeIf { it.isNotEmpty() }?.let { ids ->
                    put("movieIds", JsonArray(ids.map { JsonPrimitive(it) }))
                }
            }.toString()
            val request = try {
                Request.Builder()
                    .url(baseUrl.trimEnd('/') + "/api/v3/command")
                    .post(body.toRequestBody("application/json".toMediaTypeOrNull()))
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
