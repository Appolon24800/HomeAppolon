package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.RetagTrackResource
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** RetagTrack endpoints from the checked-in Lidarr specification. */
interface RetagTrackApi {
    @GET("api/v1/retag")
    suspend fun getTrackRetags(
        @Query("artistId") artistId: Int? = null,
        @Query("albumId") albumId: Int? = null,
    ): List<RetagTrackResource>

}
