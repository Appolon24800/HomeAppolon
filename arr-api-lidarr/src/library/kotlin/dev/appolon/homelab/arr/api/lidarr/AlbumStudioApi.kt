package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.AlbumStudioResource
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** AlbumStudio endpoints from the checked-in Lidarr specification. */
interface AlbumStudioApi {
    /** The specification omits the response schema. */
    @POST("api/v1/albumstudio")
    suspend fun updateAlbumStudio(
        @Body body: AlbumStudioResource,
    ): JsonObject

}
