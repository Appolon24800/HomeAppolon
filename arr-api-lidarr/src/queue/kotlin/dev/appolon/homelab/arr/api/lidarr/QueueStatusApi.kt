package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.QueueStatusResource
import retrofit2.http.GET

interface QueueStatusApi {
    @GET("api/v1/queue/status")
    suspend fun getQueueStatus(): QueueStatusResource
}
