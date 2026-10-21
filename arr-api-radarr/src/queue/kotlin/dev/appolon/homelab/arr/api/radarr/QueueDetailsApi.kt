package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.QueueResource
import retrofit2.http.GET
import retrofit2.http.Query

/** Radarr endpoints tagged QueueDetails. */
@JvmSuppressWildcards

interface QueueDetailsApi {
    @GET("api/v3/queue/details")
    suspend fun queueDetails(
        @Query("movieId") movieId: Int? = null,
        @Query("includeMovie") includeMovie: Boolean? = null,
    ): List<QueueResource>

}
