package dev.appolon.homelab.arr

import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.data.ServerRegistry
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
import dev.appolon.homelab.arr.net.ServerEndpoint
import dev.appolon.homelab.arr.net.ProxyConfig
import dev.appolon.homelab.arr.net.TrustSpec
import dev.appolon.homelab.arr.servers.net.ConnectionOutcome
import dev.appolon.homelab.arr.servers.net.ConnectionTester
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Verifies discovered credentials through the same client policy as manual setup. */
internal class ArrServerProvisioner(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val tester: ConnectionTester,
    private val defaultProxy: suspend () -> ProxyConfig = { ProxyConfig() },
) {
    private val gate = Mutex()

    suspend fun provision(app: AppType, baseUrl: String, apiKey: String): ServerId? = gate.withLock {
        try {
            val base = ServerEndpoint.normalizeBaseUrl(baseUrl)
            val existing = registry.servers().firstOrNull {
                it.type == app && ServerEndpoint.normalizeBaseUrl(it.baseUrl) == base
            }
            val endpoint = ServerEndpoint(
                serverId = existing?.id ?: ServerId(UUID.randomUUID().toString()),
                baseUrl = base,
                apiKey = apiKey,
                basicAuthUser = existing?.basicAuthUser,
                basicAuthPassword = existing?.basicAuthPasswordEncrypted?.let(secrets::decrypt),
                proxy = existing?.proxy ?: defaultProxy(),
                trust = existing?.trust ?: TrustSpec.System,
            )
            if (tester.test(endpoint, app) !is ConnectionOutcome.Success) return@withLock null
            registry.upsert(
                existing?.copy(apiKeyEncrypted = secrets.encrypt(apiKey))
                    ?: ArrServer(
                        id = endpoint.serverId,
                        type = app,
                        name = app.displayName,
                        baseUrl = base,
                        apiKeyEncrypted = secrets.encrypt(apiKey),
                    ),
            ).id
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}
