package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.RetagTrackResource
import retrofit2.http.GET
import retrofit2.http.Query

/** RetagTrack endpoints from the checked-in Lidarr specification. */
interface RetagTrackApi {
    @GET("api/v1/retag")
    suspend fun getTrackRetags(
        @Query("artistId") artistId: Int? = null,
        @Query("albumId") albumId: Int? = null,
    ): List<RetagTrackResource>

}
