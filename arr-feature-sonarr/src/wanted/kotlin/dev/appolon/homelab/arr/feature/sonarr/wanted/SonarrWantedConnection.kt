package dev.appolon.homelab.arr.feature.sonarr.wanted

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dev.appolon.homelab.arr.api.sonarr.CutoffApi
import dev.appolon.homelab.arr.api.sonarr.MissingApi
import dev.appolon.homelab.arr.api.sonarr.SonarrDiscoveryApis
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.data.ServerRegistry
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
import dev.appolon.homelab.arr.feature.sonarr.wanted.data.WantedCommandRunner
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
 * Everything the wanted screens need to talk to one Sonarr server, built on
 * the committed client stack: [DefaultArrClientFactory] honors proxy, basic
 * auth, and trust, and the Retrofit instance reuses that client so API calls
 * and poster fetches share one connection pool and credential path.
 */
class SonarrWantedConnection(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {

    /** The per-server API bundle; null when the server or its secrets are gone. */
    suspend fun connect(serverId: ServerId): SonarrWantedApis? {
        val server = registry.byId(serverId) ?: return null
        val endpoint = endpointOf(server) ?: return null
        val client = clientFactory.clientFor(endpoint)
        val baseUrl = ServerEndpoint.normalizeBaseUrl(server.baseUrl)
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        return SonarrWantedApis(
            missingApi = SonarrDiscoveryApis.missing(retrofit),
            cutoffApi = SonarrDiscoveryApis.cutoff(retrofit),
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
class SonarrWantedApis(
    val missingApi: MissingApi,
    val cutoffApi: CutoffApi,
    val commandRunner: WantedCommandRunner,
    val baseUrl: String,
    val client: OkHttpClient,
) {
    /**
     * Coil loader whose calls ride the same authenticated OkHttp client as
     * the API, required for local MediaCover poster paths. One instance per
     * screen is enough; Coil deduplicates requests in flight.
     */
    fun imageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(client)) }
        .crossfade(true)
        .build()
}

/**
 * Runs wanted commands over a raw POST instead of the Retrofit
 * `CommandApi`: command payloads put the selection fields `seriesId` and
 * `episodeIds` at the JSON root, a shape the committed `CommandResource`
 * DTO cannot express, and Sonarr rejects ids nested inside `body`. The body
 * is built here; the wire path, auth, and prefix handling are
 * [ArrCommandRunner].
 */
class OkHttpWantedCommandRunner(
    client: OkHttpClient,
    baseUrl: String,
) : WantedCommandRunner {

    private val runner = ArrCommandRunner(client, baseUrl)

    override suspend fun run(
        name: String,
        seriesId: Int?,
        episodeIds: List<Int>?,
    ): ArrResult<Unit> {
        val body = buildJsonObject {
            put("name", name)
            seriesId?.let { put("seriesId", it) }
            episodeIds?.let { ids ->
                put("episodeIds", JsonArray(ids.map { id -> JsonPrimitive(id) }))
            }
        }.toString()
        return withContext(Dispatchers.IO) { runner.post(COMMAND_PATH, body) }
    }

    private companion object {
        const val COMMAND_PATH = "api/v3/command"
    }
}
