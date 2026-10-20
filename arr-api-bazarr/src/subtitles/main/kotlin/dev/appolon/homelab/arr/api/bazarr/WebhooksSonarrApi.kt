package dev.appolon.homelab.arr.api.bazarr

import dev.appolon.homelab.arr.api.bazarr.model.SonarrWebhook
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.POST

interface WebhooksSonarrApi {
    @POST("api/webhooks/sonarr")
    suspend fun postWebHooksSonarr(@Body payload: SonarrWebhook): JsonObject
}
