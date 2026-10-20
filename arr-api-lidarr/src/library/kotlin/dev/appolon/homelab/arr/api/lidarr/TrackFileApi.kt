package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.TrackFileListResource
import dev.appolon.homelab.arr.api.lidarr.model.TrackFileResource
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** TrackFile endpoints from the checked-in Lidarr specification. */
interface TrackFileApi {
    @GET("api/v1/trackfile/{id}")
    suspend fun getTrackFile(
        @Path("id") id: Int,
    ): TrackFileResource

    @PUT("api/v1/trackfile/{id}")
    suspend fun updateTrackFile(
        @Path("id") id: String,
        @Body body: TrackFileResource,
    ): TrackFileResource

    /** The specification omits the response schema. */
    @DELETE("api/v1/trackfile/{id}")
    suspend fun deleteTrackFile(
        @Path("id") id: Int,
    ): JsonObject

    @GET("api/v1/trackfile")
    suspend fun getTrackFiles(
        @Query("artistId") artistId: Int? = null,
        @Query("trackFileIds") trackFileIds: List<Int>? = null,
        @Query("albumId") albumId: List<Int>? = null,
        @Query("unmapped") unmapped: Boolean? = null,
    ): List<TrackFileResource>

    /** The specification omits the response schema. */
    @PUT("api/v1/trackfile/editor")
    suspend fun updateTrackFiles(
        @Body body: TrackFileListResource,
    ): JsonObject

    /** The specification omits the response schema. */
    @HTTP(method = "DELETE", path = "api/v1/trackfile/bulk", hasBody = true)
    suspend fun deleteTrackFiles(
        @Body body: TrackFileListResource,
    ): JsonObject

}
