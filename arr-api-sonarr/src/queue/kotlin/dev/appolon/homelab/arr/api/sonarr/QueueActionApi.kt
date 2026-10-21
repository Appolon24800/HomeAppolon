package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.QueueBulkResource
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/** Operations in the Sonarr QueueAction OpenAPI tag. */
@JvmSuppressWildcards
interface QueueActionApi {
    @POST("api/v3/queue/grab/{id}")
    suspend fun create(
        @Path("id") id: Int
    ): Unit

    @POST("api/v3/queue/grab/bulk")
    suspend fun createBulk(
        @Body body: QueueBulkResource
    ): Unit
}
