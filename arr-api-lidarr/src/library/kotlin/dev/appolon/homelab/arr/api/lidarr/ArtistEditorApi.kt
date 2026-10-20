package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.ArtistEditorResource
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.HTTP
import retrofit2.http.PUT

/** ArtistEditor endpoints from the checked-in Lidarr specification. */
interface ArtistEditorApi {
    /** The specification omits the response schema. */
    @PUT("api/v1/artist/editor")
    suspend fun updateArtists(
        @Body body: ArtistEditorResource,
    ): JsonObject

    /** The specification omits the response schema. */
    @HTTP(method = "DELETE", path = "api/v1/artist/editor", hasBody = true)
    suspend fun deleteArtists(
        @Body body: ArtistEditorResource,
    ): JsonObject

}
