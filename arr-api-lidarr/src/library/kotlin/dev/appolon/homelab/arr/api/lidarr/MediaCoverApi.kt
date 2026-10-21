package dev.appolon.homelab.arr.api.lidarr

import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Path

/** MediaCover endpoints from the checked-in Lidarr specification. */
interface MediaCoverApi {
    /** The specification omits the response schema. */
    @GET("api/v1/mediacover/artist/{artistId}/{filename}")
    suspend fun getArtistCover(
        @Path("artistId") artistId: Int,
        @Path("filename") filename: String,
    ): JsonObject

    /** The specification omits the response schema. */
    @GET("api/v1/mediacover/album/{albumId}/{filename}")
    suspend fun getAlbumCover(
        @Path("albumId") albumId: Int,
        @Path("filename") filename: String,
    ): JsonObject

}
