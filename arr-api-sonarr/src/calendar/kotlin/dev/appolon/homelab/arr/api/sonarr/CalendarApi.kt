package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Operations in the Sonarr Calendar OpenAPI tag. */
@JvmSuppressWildcards
interface CalendarApi {
    @GET("api/v3/calendar")
    suspend fun list(
        @Query("start") start: String? = null,
        @Query("end") end: String? = null,
        @Query("unmonitored") unmonitored: Boolean? = null,
        @Query("includeSeries") includeSeries: Boolean? = null,
        @Query("includeEpisodeFile") includeEpisodeFile: Boolean? = null,
        @Query("includeEpisodeImages") includeEpisodeImages: Boolean? = null,
        @Query("tags") tags: String? = null
    ): List<EpisodeResource>

    @GET("api/v3/calendar/{id}")
    suspend fun get(
        @Path("id") id: Int
    ): Response<EpisodeResource>
}
