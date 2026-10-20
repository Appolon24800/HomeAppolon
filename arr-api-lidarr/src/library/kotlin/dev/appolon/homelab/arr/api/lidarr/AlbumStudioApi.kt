package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.AlbumStudioResource
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.POST

/** AlbumStudio endpoints from the checked-in Lidarr specification. */
interface AlbumStudioApi {
    /** The specification omits the response schema. */
    @POST("api/v1/albumstudio")
    suspend fun updateAlbumStudio(
        @Body body: AlbumStudioResource,
    ): JsonObject

}
