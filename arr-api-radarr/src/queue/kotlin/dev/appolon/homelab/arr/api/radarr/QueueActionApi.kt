package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.QueueBulkResource
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/** Radarr endpoints tagged QueueAction. */
@JvmSuppressWildcards

interface QueueActionApi {
    /** The spec leaves the response schema unspecified. */
    @POST("api/v3/queue/grab/{id}")
    suspend fun grabQueue(
        @Path("id") id: Int,
    ): Response<JsonObject>

    /** The spec leaves the response schema unspecified. */
    @POST("api/v3/queue/grab/bulk")
    suspend fun grabQueueBulk(
        @Body body: QueueBulkResource,
    ): Response<JsonObject>

}
