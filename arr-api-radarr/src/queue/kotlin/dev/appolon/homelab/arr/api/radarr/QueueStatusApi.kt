package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.QueueStatusResource
import retrofit2.http.GET

/** Radarr endpoints tagged QueueStatus. */
@JvmSuppressWildcards

interface QueueStatusApi {
    @GET("api/v3/queue/status")
    suspend fun queueStatus(): QueueStatusResource

}
