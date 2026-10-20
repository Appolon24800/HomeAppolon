package dev.appolon.homelab.arr.api.bazarr

import kotlin.jvm.JvmSuppressWildcards
import retrofit2.Response
import retrofit2.http.POST

/**
 * Webhook connection probe for the systemSettings tag. The bundled spec declares neither a
 * request body nor a response schema: the HTTP status alone is the contract, so the operation
 * returns [Response] and discards any body the server sends.
 */
@JvmSuppressWildcards
interface SystemSettingsApi {
    @POST("api/system/webhooks/test")
    suspend fun postSystemWebhookTest(): Response<Unit>
}
