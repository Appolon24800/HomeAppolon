package dev.appolon.homelab.arr.feature.radarr.detail

import dev.appolon.homelab.arr.api.radarr.RadarrApis
import dev.appolon.homelab.arr.api.radarr.RadarrCalendarApis
import dev.appolon.homelab.arr.api.radarr.RadarrQueueApis
import dev.appolon.homelab.arr.api.radarr.RadarrSystemApis
import dev.appolon.homelab.arr.api.radarr.api.CreditApi
import dev.appolon.homelab.arr.api.radarr.api.ExtraFileApi
import dev.appolon.homelab.arr.api.radarr.api.MovieApi
import dev.appolon.homelab.arr.api.radarr.api.MovieFileApi
import dev.appolon.homelab.arr.api.radarr.api.MovieFolderApi
import dev.appolon.homelab.arr.api.radarr.model.CommandStatus
import dev.appolon.homelab.arr.core.ArrError
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.net.URI

/** Every Retrofit interface the movie detail screens call. */
data class RadarrDetailApis(
    val movie: MovieApi,
    val movieFile: MovieFileApi,
    val credit: CreditApi,
    val extraFile: ExtraFileApi,
    val movieFolder: MovieFolderApi,
    val history: dev.appolon.homelab.arr.api.radarr.HistoryApi,
    val manualImport: dev.appolon.homelab.arr.api.radarr.ManualImportApi,
    val tag: dev.appolon.homelab.arr.api.radarr.TagApi,
    val qualityProfile: dev.appolon.homelab.arr.api.radarr.QualityProfileApi,
)

/** Minimal result of a started command; Radarr executes it asynchronously. */
data class CommandReceipt(
    val id: Int,
    val name: String?,
    val status: CommandStatus?,
)

/**
 * Server access seam for the detail feature. Production talks to Retrofit
 * interfaces built on the shared client factory; tests supply an in-memory
 * fake. Command posts ride the raw client because the committed
 * `CommandResource` DTO cannot express movie-scoped arguments
 * (`movieIds`, `movieId`, `path`) the server expects next to `name`.
 */
interface RadarrDetailConnection {
    /** Null when the server is unknown or its stored key cannot be decrypted. */
    suspend fun apis(serverId: ServerId): RadarrDetailApis?

    suspend fun baseUrlOf(serverId: ServerId): String?

    /**
     * Posts one command body to `api/v3/command`. Network failures come back
     * as [ArrError.Network] so callers can queue them for replay.
     */
    suspend fun postCommand(serverId: ServerId, body: JsonObject): ArrResult<CommandReceipt>

    /**
     * OkHttp client for Coil that stamps auth headers only on requests to the
     * server's own host, so remote TMDB artwork never receives the API key.
     */
    suspend fun imageLoaderClient(serverId: ServerId): OkHttpClient?
}

/**
 * Production [RadarrDetailConnection]: one Retrofit instance per server,
 * cached, built over [DefaultArrClientFactory] so proxy, basic auth, and TLS
 * trust match every other call this app makes.
 */
class HttpRadarrDetailConnection(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) : RadarrDetailConnection {

    private val retrofitCache = HashMap<ServerId, Pair<String, Retrofit>>()

    override suspend fun apis(serverId: ServerId): RadarrDetailApis? =
        retrofitFor(serverId)?.let { retrofit ->
            RadarrDetailApis(
                movie = RadarrApis.movie(retrofit),
                movieFile = RadarrApis.movieFile(retrofit),
                credit = RadarrApis.credit(retrofit),
                extraFile = RadarrApis.extraFile(retrofit),
                movieFolder = RadarrApis.movieFolder(retrofit),
                history = RadarrQueueApis.history(retrofit),
                manualImport = RadarrQueueApis.manualImport(retrofit),
                tag = RadarrSystemApis.tag(retrofit),
                qualityProfile = RadarrCalendarApis.qualityProfile(retrofit),
            )
        }

    override suspend fun baseUrlOf(serverId: ServerId): String? =
        serverOf(serverId)?.baseUrl

    override suspend fun postCommand(
        serverId: ServerId,
        body: JsonObject,
    ): ArrResult<CommandReceipt> = withContext(Dispatchers.IO) {
        val server = serverOf(serverId) ?: return@withContext ArrResult.err(ArrError.Offline())
        val endpoint = endpointOf(server) ?: return@withContext ArrResult.err(ArrError.Offline())
        val url = ServerEndpoint.normalizeBaseUrl(server.baseUrl) + "api/v3/command"
        val request = okhttp3.Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val response = try {
            clientFactory.clientFor(endpoint).newCall(request).execute()
        } catch (io: IOException) {
            return@withContext ArrResult.err(io.arrError())
        }
        response.use { r ->
            if (!r.isSuccessful) return@withContext ArrResult.err(r.arrError())
            val parsed = runCatching {
                ArrJson.parseToJsonElement(r.body?.string().orEmpty()).jsonObject
            }.getOrNull() ?: JsonObject(emptyMap())
            val name = runCatching { parsed["name"]!!.jsonPrimitive.content }.getOrNull()
            val status = runCatching {
                CommandStatus.valueOf(parsed["status"]!!.jsonPrimitive.content)
            }.getOrNull()
            ArrResult.ok(
                CommandReceipt(
                    id = parsed["id"]?.jsonPrimitive?.intOrNull ?: 0,
                    name = name,
                    status = status,
                ),
            )
        }
    }

    override suspend fun imageLoaderClient(serverId: ServerId): OkHttpClient? {
        val server = serverOf(serverId) ?: return null
        val endpoint = endpointOf(server) ?: return null
        val host = runCatching { URI(ServerEndpoint.normalizeBaseUrl(server.baseUrl)).host }
            .getOrNull() ?: return null
        val base = clientFactory.clientFor(endpoint)
        // The shared client stamps the API key on every request; images may
        // come from TMDB, so replace that interceptor with a host-scoped one.
        val builder = base.newBuilder()
        builder.interceptors().clear()
        builder.addInterceptor(HostScopedHeadersInterceptor(host, endpoint))
        return builder.build()
    }

    private suspend fun serverOf(serverId: ServerId): ArrServer? = registry.byId(serverId)

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

    private suspend fun retrofitFor(serverId: ServerId): Retrofit? {
        val server = serverOf(serverId) ?: return null
        val cached = synchronized(retrofitCache) { retrofitCache[serverId] }
        if (cached != null && cached.first == server.baseUrl) return cached.second
        val endpoint = endpointOf(server) ?: return null
        val retrofit = Retrofit.Builder()
            .baseUrl(ServerEndpoint.normalizeBaseUrl(server.baseUrl))
            .client(clientFactory.clientFor(endpoint))
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        synchronized(retrofitCache) { retrofitCache[serverId] = server.baseUrl to retrofit }
        return retrofit
    }
}

/**
 * Stamps identity headers only when the request targets [host]: local
 * mediacover URLs need the API key, TMDB artwork must never see it.
 */
private class HostScopedHeadersInterceptor(
    private val host: String,
    private val endpoint: ServerEndpoint,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val toServer = chain.request().url.host.equals(host, ignoreCase = true)
        if (!toServer) return chain.proceed(chain.request())
        val user = endpoint.basicAuthUser
        val password = endpoint.basicAuthPassword
        val request = chain.request().newBuilder()
            .header("X-Api-Key", endpoint.apiKey)
            .header("User-Agent", "HomelabArr/1.0 (android)")
            .apply {
                if (!user.isNullOrEmpty() && !password.isNullOrEmpty()) {
                    header("Authorization", Credentials.basic(user, password))
                }
            }
            .build()
        return chain.proceed(request)
    }
}

/**
 * Maps repository-level throwables onto the shared taxonomy: Retrofit's
 * [HttpException] keeps its status code, transport failures stay
 * [ArrError.Network], everything else is [ArrError.Unknown].
 */
fun Throwable.toArrError(): ArrError = when (this) {
    is HttpException -> when (code()) {
        401, 403 -> ArrError.Auth(code())
        else -> ArrError.Http(code(), message())
    }
    is IOException -> ArrError.Network(this)
    else -> ArrError.Unknown(this)
}
