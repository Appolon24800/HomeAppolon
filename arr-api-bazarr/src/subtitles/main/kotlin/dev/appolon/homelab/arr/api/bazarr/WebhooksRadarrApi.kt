package dev.appolon.homelab.arr.api.bazarr

import dev.appolon.homelab.arr.api.bazarr.model.RadarrWebhook
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.POST

interface WebhooksRadarrApi {
    @POST("api/webhooks/radarr")
    suspend fun postWebHooksRadarr(@Body payload: RadarrWebhook): JsonObject
}
