package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.RenameEpisodeResource
import retrofit2.http.GET
import retrofit2.http.Query

interface RenameEpisodeApi {
    @GET("api/v3/rename")
    suspend fun list(
        @Query("seriesId") seriesId: Int? = null,
        @Query("seasonNumber") seasonNumber: Int? = null
    ): List<RenameEpisodeResource>

    @GET("api/v3/rename/bulk")
    suspend fun listBulk(
        @Query("seriesIds") seriesIds: List<Int>? = null
    ): List<RenameEpisodeResource>
}
