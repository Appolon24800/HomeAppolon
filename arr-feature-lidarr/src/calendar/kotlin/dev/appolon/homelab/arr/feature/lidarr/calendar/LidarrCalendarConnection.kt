package dev.appolon.homelab.arr.feature.lidarr.calendar

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dev.appolon.homelab.arr.api.lidarr.CalendarApi
import dev.appolon.homelab.arr.api.lidarr.LidarrCalendarApis
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
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Everything the calendar screen needs to talk to one Lidarr server, built
 * on the committed client stack: [DefaultArrClientFactory] honors proxy,
 * basic auth, and trust, and the Retrofit instance reuses that client so
 * calendar calls and cover fetches share one connection pool and one
 * credential path. Cover bytes themselves load through Coil
 * ([LidarrCalendarApisBundle.imageLoader]), never through Retrofit.
 */
class LidarrCalendarConnection(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {

    /** The per-server API bundle; null when the server or its secrets are gone. */
    suspend fun connect(serverId: ServerId): LidarrCalendarApisBundle? {
        val server = registry.byId(serverId) ?: return null
        val endpoint = endpointOf(server) ?: return null
        val client = clientFactory.clientFor(endpoint)
        val baseUrl = ServerEndpoint.normalizeBaseUrl(server.baseUrl)
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        return LidarrCalendarApisBundle(
            calendarApi = LidarrCalendarApis.calendar(retrofit),
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

/** Per-server API bundle handed to the calendar repository. */
class LidarrCalendarApisBundle(
    val calendarApi: CalendarApi,
    val baseUrl: String,
    val client: OkHttpClient,
) {
    /**
     * Coil loader whose calls ride the same authenticated OkHttp client as
     * the API, required for local album MediaCover paths.
     */
    fun imageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(client)) }
        .build()
}
