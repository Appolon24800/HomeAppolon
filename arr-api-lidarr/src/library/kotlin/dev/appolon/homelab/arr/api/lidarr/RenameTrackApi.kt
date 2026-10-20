package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.RenameTrackResource
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** RenameTrack endpoints from the checked-in Lidarr specification. */
interface RenameTrackApi {
    @GET("api/v1/rename")
    suspend fun getTrackRenames(
        @Query("artistId") artistId: Int? = null,
        @Query("albumId") albumId: Int? = null,
    ): List<RenameTrackResource>

}
