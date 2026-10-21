package dev.appolon.homelab.arr.servers.net

import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.net.ArrClientFactory
import dev.appolon.homelab.arr.net.DefaultArrClientFactory
import dev.appolon.homelab.arr.net.ServerEndpoint
import dev.appolon.homelab.arr.net.arrError
import dev.appolon.homelab.arr.servers.logic.StatusEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/** Result of one connection test: reachable (with the reported version) or a mapped failure. */
sealed interface ConnectionOutcome {
    data class Success(val version: String?) : ConnectionOutcome

    data class Failure(val error: ArrError) : ConnectionOutcome
}

/** One-shot connectivity probe against a server's status endpoint. */
fun interface ConnectionTester {
    suspend fun test(endpoint: ServerEndpoint, appType: AppType): ConnectionOutcome
}

/**
 * Connection test through the production client stack: the same
 * [DefaultArrClientFactory] path real API calls take, so a green test also
 * validates proxy, basic auth, and TLS-trust settings. All probes share one
 * synthetic server id that is invalidated after each call, so the factory's
 * client cache never accumulates probe credentials.
 */
class ServerConnectionTester(
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) : ConnectionTester {

    override suspend fun test(endpoint: ServerEndpoint, appType: AppType): ConnectionOutcome =
        withContext(Dispatchers.IO) {
            val response = try {
                val request = Request.Builder()
                    .url(StatusEndpoint.urlFor(endpoint.baseUrl, appType))
                    .get()
                    .build()
clientFactory.clientFor(endpoint.copy(serverId = PROBE_ID)).newCall(request)
                    .execute()
            } catch (t: Throwable) {
                return@withContext ConnectionOutcome.Failure(t.arrError())
            } finally {
                clientFactory.invalidate(PROBE_ID)
            }
            response.use { r ->
                if (r.isSuccessful) {
                    val body = r.body?.string().orEmpty()
                    ConnectionOutcome.Success(StatusEndpoint.extractVersion(body))
                } else {
                    ConnectionOutcome.Failure(r.arrError())
                }
            }
        }

    private companion object {
        val PROBE_ID = ServerId("__connection_test__")
    }
}
