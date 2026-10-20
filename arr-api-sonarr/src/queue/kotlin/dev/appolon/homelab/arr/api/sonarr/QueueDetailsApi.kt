package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.QueueResource
import retrofit2.http.GET
import retrofit2.http.Query

/** Operations in the Sonarr QueueDetails OpenAPI tag. */
@JvmSuppressWildcards
interface QueueDetailsApi {
    @GET("api/v3/queue/details")
    suspend fun list(
        @Query("seriesId") seriesId: Int? = null,
        @Query("episodeIds") episodeIds: List<Int>? = null,
        @Query("includeSeries") includeSeries: Boolean? = null,
        @Query("includeEpisode") includeEpisode: Boolean? = null
    ): List<QueueResource>
}
