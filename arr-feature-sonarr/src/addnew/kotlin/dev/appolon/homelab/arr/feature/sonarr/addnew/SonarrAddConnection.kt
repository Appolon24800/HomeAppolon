package dev.appolon.homelab.arr.feature.sonarr.addnew

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dev.appolon.homelab.arr.api.sonarr.ImportListApi
import dev.appolon.homelab.arr.api.sonarr.ImportListExclusionApi
import dev.appolon.homelab.arr.api.sonarr.SeriesApi
import dev.appolon.homelab.arr.api.sonarr.SonarrCalendarApis
import dev.appolon.homelab.arr.api.sonarr.SonarrDiscoveryApis
import dev.appolon.homelab.arr.api.sonarr.SonarrLibraryApis
import dev.appolon.homelab.arr.api.sonarr.SonarrSystemApis
import dev.appolon.homelab.arr.api.sonarr.model.LanguageProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.RootFolderResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.api.sonarr.model.TagResource
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
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Everything the add-new screen needs to talk to one Sonarr server, built on
 * the committed client stack exactly like the library connection: proxy,
 * basic auth, and trust come from [DefaultArrClientFactory], and the Retrofit
 * instance reuses that client so API calls and poster fetches share one
 * connection pool.
 */
class SonarrAddConnection(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {

    /** The per-server API bundle; null when the server or its secrets are gone. */
    suspend fun connect(serverId: ServerId): SonarrAddApis? {
        val server = registry.byId(serverId) ?: return null
        val endpoint = endpointOf(server) ?: return null
        val client = clientFactory.clientFor(endpoint)
        val baseUrl = ServerEndpoint.normalizeBaseUrl(server.baseUrl)
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        val qualityProfileApi = SonarrCalendarApis.qualityProfile(retrofit)
        val languageProfileApi = SonarrCalendarApis.languageProfile(retrofit)
        val rootFolderApi = SonarrSystemApis.rootFolder(retrofit)
        val tagApi = SonarrSystemApis.tag(retrofit)
        return SonarrAddApis(
            seriesApi = SonarrLibraryApis.series(retrofit),
            lookupRunner = OkHttpSonarrSeriesLookupRunner(client, baseUrl),
            qualityProfiles = { qualityProfileApi.list() },
            languageProfiles = { languageProfileApi.list() },
            rootFolders = { rootFolderApi.list() },
            tags = { tagApi.list() },
            importListApi = SonarrDiscoveryApis.importList(retrofit),
            importListExclusionApi = SonarrDiscoveryApis.importListExclusion(retrofit),
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

/** Per-server API bundle handed to the add repository. */
class SonarrAddApis(
    val seriesApi: SeriesApi,
    val lookupRunner: SonarrSeriesLookupRunner,
    val qualityProfiles: suspend () -> List<QualityProfileResource>,
    val languageProfiles: suspend () -> List<LanguageProfileResource>,
    val rootFolders: suspend () -> List<RootFolderResource>,
    val tags: suspend () -> List<TagResource>,
    val importListApi: ImportListApi,
    val importListExclusionApi: ImportListExclusionApi,
    val baseUrl: String,
    val client: OkHttpClient,
) {
    /**
     * Coil loader riding the same authenticated OkHttp client as the API,
     * required whenever a lookup result resolves to a local cover path. One
     * instance per screen is enough; Coil deduplicates requests in flight.
     */
    fun imageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(client)) }
        .build()
}

/**
 * Series search against Sonarr's metadata proxy. Implemented as a raw GET,
 * not a Retrofit interface, because the committed `:arr-api-sonarr` module
 * ships no `SeriesLookupApi`; the response is a plain `List<SeriesResource>`,
 * so decoding it here keeps the same wire path, auth, and prefix handling as
 * every other call without touching files this task does not own.
 */
fun interface SonarrSeriesLookupRunner {
    suspend fun lookup(term: String): ArrResult<List<SeriesResource>>
}

/** [SonarrSeriesLookupRunner] over the shared authenticated OkHttp client. */
class OkHttpSonarrSeriesLookupRunner(
    private val client: OkHttpClient,
    private val baseUrl: String,
) : SonarrSeriesLookupRunner {

    override suspend fun lookup(term: String): ArrResult<List<SeriesResource>> =
        withContext(Dispatchers.IO) {
            val url = baseUrl.trimEnd('/')
                .toHttpUrlOrNull()
                ?.newBuilder()
                ?.addPathSegments("api/v3/series/lookup")
                ?.addQueryParameter("term", term)
                ?.build()
                ?: return@withContext ArrResult.err(
                    ArrError.Parse(IllegalArgumentException("Unusable base URL: $baseUrl")),
                )
            val request = Request.Builder().url(url).get().build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext ArrResult.err(response.arrError())
                    }
                    val body = response.body.string()
                    ArrResult.ok(
                        ArrJson.decodeFromString(LIST_OF_SERIES, body),
                    )
                }
            } catch (e: IOException) {
                ArrResult.err(ArrError.Network(e))
            } catch (e: SerializationException) {
                ArrResult.err(ArrError.Parse(e))
            } catch (t: Throwable) {
                ArrResult.err(t.arrError())
            }
        }

    private companion object {
        private val LIST_OF_SERIES = ListSerializer(SeriesResource.serializer())
    }
}
