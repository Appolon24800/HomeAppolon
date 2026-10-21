package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.QueueStatusResource
import retrofit2.http.GET

/** Operations in the Sonarr QueueStatus OpenAPI tag. */
@JvmSuppressWildcards
interface QueueStatusApi {
    @GET("api/v3/queue/status")
    suspend fun list(): QueueStatusResource
}
