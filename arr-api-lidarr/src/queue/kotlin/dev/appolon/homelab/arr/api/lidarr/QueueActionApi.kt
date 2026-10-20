package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.QueueBulkResource
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface QueueActionApi {
    @POST("api/v1/queue/grab/{id}")
    suspend fun grabQueue(@Path("id") id: Int)

    @POST("api/v1/queue/grab/bulk")
    suspend fun grabQueueBulk(@Body body: QueueBulkResource)
}
