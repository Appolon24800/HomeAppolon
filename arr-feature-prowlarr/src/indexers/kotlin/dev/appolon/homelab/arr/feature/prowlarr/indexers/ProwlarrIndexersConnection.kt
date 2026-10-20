package dev.appolon.homelab.arr.feature.prowlarr.indexers

import dev.appolon.homelab.arr.api.prowlarr.AppProfileApi
import dev.appolon.homelab.arr.api.prowlarr.ApplicationApi
import dev.appolon.homelab.arr.api.prowlarr.IndexerApi
import dev.appolon.homelab.arr.api.prowlarr.IndexerDefaultCategoriesApi
import dev.appolon.homelab.arr.api.prowlarr.IndexerProxyApi
import dev.appolon.homelab.arr.api.prowlarr.IndexerStatusApi
import dev.appolon.homelab.arr.api.prowlarr.ProwlarrIndexersApis
import dev.appolon.homelab.arr.api.prowlarr.TagApi
import dev.appolon.homelab.arr.api.prowlarr.TagDetailsApi
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.data.ServerRegistry
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
import dev.appolon.homelab.arr.net.ArrClientFactory
import dev.appolon.homelab.arr.net.ArrCommandRunner
import dev.appolon.homelab.arr.net.DefaultArrClientFactory
import dev.appolon.homelab.arr.net.ServerEndpoint
import dev.appolon.homelab.arr.net.TrustSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Everything the indexer feature needs to talk to one Prowlarr server. The
 * Retrofit instance rides the committed client stack so proxy, basic auth,
 * and trust handling match every other arr feature.
 */
class ProwlarrIndexersConnection(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {

    /** The per-server API bundle; null when the server or its secrets are gone. */
    suspend fun connect(serverId: ServerId): ProwlarrIndexersFeatureApis? {
        val server = registry.byId(serverId) ?: return null
        val endpoint = endpointOf(server) ?: return null
        val client = clientFactory.clientFor(endpoint)
        val baseUrl = ServerEndpoint.normalizeBaseUrl(server.baseUrl)
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(
                dev.appolon.homelab.arr.net.ArrJson
                    .asConverterFactory("application/json".toMediaType()),
            )
            .build()
        return ProwlarrIndexersFeatureApis(
            indexerApi = ProwlarrIndexersApis.indexer(retrofit),
            categoriesApi = ProwlarrIndexersApis.indexerDefaultCategories(retrofit),
            statusApi = ProwlarrIndexersApis.indexerStatus(retrofit),
            proxyApi = ProwlarrIndexersApis.indexerProxy(retrofit),
            appProfileApi = ProwlarrIndexersApis.appProfile(retrofit),
            applicationApi = ProwlarrIndexersApis.application(retrofit),
            tagApi = ProwlarrIndexersApis.tag(retrofit),
            tagDetailsApi = ProwlarrIndexersApis.tagDetails(retrofit),
            commandRunner = OkHttpProwlarrCommandRunner(client, baseUrl),
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
class ProwlarrIndexersFeatureApis(
    val indexerApi: IndexerApi,
    val categoriesApi: IndexerDefaultCategoriesApi,
    val statusApi: IndexerStatusApi,
    val proxyApi: IndexerProxyApi,
    val appProfileApi: AppProfileApi,
    val applicationApi: ApplicationApi,
    val tagApi: TagApi,
    val tagDetailsApi: TagDetailsApi,
    val commandRunner: ProwlarrCommandRunner,
)

/** Runs one named Prowlarr command, used for the application sync button. */
fun interface ProwlarrCommandRunner {
    suspend fun run(name: String): ArrResult<Unit>
}

/**
 * Commands over a raw POST: the command payload is a bare `{"name":...}`
 * object at the JSON root, and the feature only ever needs the name, so a
 * dedicated Retrofit interface would add nothing. The body is built here;
 * auth, path prefix, and client behavior are [ArrCommandRunner].
 */
class OkHttpProwlarrCommandRunner(
    client: OkHttpClient,
    baseUrl: String,
) : ProwlarrCommandRunner {

    private val runner = ArrCommandRunner(client, baseUrl)

    override suspend fun run(name: String): ArrResult<Unit> =
        withContext(Dispatchers.IO) {
        runner.post("/api/v1/command", buildJsonObject { put("name", name) }.toString())
    }
}
