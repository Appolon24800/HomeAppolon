package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.TrackResource
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Track endpoints from the checked-in Lidarr specification. */
interface TrackApi {
    @GET("api/v1/track")
    suspend fun getTracks(
        @Query("artistId") artistId: Int? = null,
        @Query("albumId") albumId: Int? = null,
        @Query("albumReleaseId") albumReleaseId: Int? = null,
        @Query("trackIds") trackIds: List<Int>? = null,
    ): List<TrackResource>

    @GET("api/v1/track/{id}")
    suspend fun getTrack(
        @Path("id") id: Int,
    ): TrackResource

}
