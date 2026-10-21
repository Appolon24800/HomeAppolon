package dev.appolon.homelab.arr.servers.net

import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.data.QueuedAction
import dev.appolon.homelab.arr.data.ServerRegistry
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
import dev.appolon.homelab.arr.net.ArrClientFactory
import dev.appolon.homelab.arr.net.DefaultArrClientFactory
import dev.appolon.homelab.arr.net.ServerEndpoint
import dev.appolon.homelab.arr.net.TrustSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Replays one [QueuedAction] against its server with the production client
 * stack, honoring the server's stored proxy, trust, and basic auth. Returns
 * false on any transport or non-2xx outcome so the caller (the action queue)
 * keeps the action and bumps its attempt count; the response body carries no
 * meaning worth surfacing here.
 */
private const val SECRET_SENTINEL = "__SECRET__"

class QueuedActionExecutor(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
) {
    suspend fun execute(action: QueuedAction): Boolean = withContext(Dispatchers.IO) {
        val server = registry.byId(action.serverId) ?: return@withContext false
        // Provider secrets are redacted to a sentinel at rest and substituted
        // only by their own repository's replay path. This generic executor
        // must never send the sentinel to a server; fail the action instead
        // so it retries or dead-letters visibly.
        if (action.bodyJson?.contains(SECRET_SENTINEL) == true) return@withContext false
        val endpoint = endpointOf(server) ?: return@withContext false
        val url = server.baseUrl.trimEnd('/') + "/" + action.path.trimStart('/')
        val method = action.method.uppercase()
        val body = action.bodyJson
            ?.toRequestBody("application/json".toMediaTypeOrNull())
        val requestBody = when {
            body != null -> body

            method in BODYLESS_METHODS -> null

            // OkHttp rejects a bodyless POST/PUT, so empty bytes stand in for
            // actions enqueued without a payload.
            else -> ByteArray(0).toRequestBody(null)
        }
        val request = try {
            Request.Builder().url(url).method(method, requestBody).build()
        } catch (t: Throwable) {
            return@withContext false
        }
        try {
            clientFactory.clientFor(endpoint).newCall(request).execute().use { it.isSuccessful }
        } catch (t: Throwable) {
            false
        }
    }

    /** Null when the stored secrets cannot be decrypted; the action then stays queued. */
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

    private companion object {
        val BODYLESS_METHODS = setOf("GET", "HEAD")
    }
}
