package dev.appolon.homelab.arr.api.bazarr

import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.POST
import retrofit2.http.Query

interface WebhooksPlexApi {
    /** A 204 means the event was ignored, unlike a processed 200 response. */
    @POST("api/webhooks/plex")
    suspend fun postWebHooksPlex(@Query("payload") payload: String): Response<JsonObject>
}
