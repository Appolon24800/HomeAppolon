package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.ReleaseResource
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** Operations in the Sonarr Release OpenAPI tag. */
@JvmSuppressWildcards
interface ReleaseApi {
    @POST("api/v3/release")
    suspend fun create(
        @Body body: ReleaseResource
    ): Unit

    @GET("api/v3/release")
    suspend fun list(
        @Query("seriesId") seriesId: Int? = null,
        @Query("episodeId") episodeId: Int? = null,
        @Query("seasonNumber") seasonNumber: Int? = null
    ): List<ReleaseResource>
}
