package dev.appolon.homelab.arr.feature.lidarr.wanted

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dev.appolon.homelab.arr.api.lidarr.CutoffApi
import dev.appolon.homelab.arr.api.lidarr.LidarrDiscoveryApis
import dev.appolon.homelab.arr.api.lidarr.MissingApi
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.data.ServerRegistry
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
import dev.appolon.homelab.arr.feature.lidarr.queue.data.QueueCommandBody
import dev.appolon.homelab.arr.feature.lidarr.wanted.data.WantedCommandRunner
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
 * Everything the wanted screens need to talk to one Lidarr server, built on
 * the committed client stack: [DefaultArrClientFactory] honors proxy, basic
 * auth, and trust, and the Retrofit instance reuses that client so API calls
 * and cover fetches share one connection pool and credential path.
 */
class LidarrWantedConnection(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {

    /** The per-server API bundle; null when the server or its secrets are gone. */
    suspend fun connect(serverId: ServerId): LidarrWantedApis? {
        val server = registry.byId(serverId) ?: return null
        val endpoint = endpointOf(server) ?: return null
        val client = clientFactory.clientFor(endpoint)
        val baseUrl = ServerEndpoint.normalizeBaseUrl(server.baseUrl)
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        return LidarrWantedApis(
            missingApi = LidarrDiscoveryApis.missing(retrofit),
            cutoffApi = LidarrDiscoveryApis.cutoff(retrofit),
            commandRunner = OkHttpWantedCommandRunner(client, baseUrl),
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
class LidarrWantedApis(
    val missingApi: MissingApi,
    val cutoffApi: CutoffApi,
    val commandRunner: WantedCommandRunner,
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
 * Runs wanted commands over a raw POST instead of the Retrofit
 * `CommandApi`: command payloads put the selection field `albumIds` at the
 * JSON root, a shape the committed `CommandResource` DTO cannot express, and
 * Lidarr rejects ids nested inside `body`. Wire path, auth, and prefix
 * handling are identical to every other call, and the body encodes through
 * [QueueCommandBody] so an enqueued replay sends byte-identical requests.
 */
class OkHttpWantedCommandRunner(
    private val client: OkHttpClient,
    private val baseUrl: String,
) : WantedCommandRunner {

    override suspend fun run(
        name: String,
        albumIds: List<Int>?,
    ): ArrResult<Unit> = withContext(Dispatchers.IO) {
        val body = QueueCommandBody(name = name, albumIds = albumIds).encode(ArrJson)
        val request = try {
            Request.Builder()
                .url(baseUrl.trimEnd('/') + "/" + COMMAND_PATH)
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

    private companion object {
        const val COMMAND_PATH = "api/v1/command"
    }
}
