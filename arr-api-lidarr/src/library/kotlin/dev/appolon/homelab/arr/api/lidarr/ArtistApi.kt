package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Artist endpoints from the checked-in Lidarr specification. */
interface ArtistApi {
    @GET("api/v1/artist/{id}")
    suspend fun getArtist(
        @Path("id") id: Int,
    ): ArtistResource

    @PUT("api/v1/artist/{id}")
    suspend fun updateArtist(
        @Path("id") id: String,
        @Query("moveFiles") moveFiles: Boolean? = null,
        @Body body: ArtistResource,
    ): ArtistResource

    /** The specification omits the response schema. */
    @DELETE("api/v1/artist/{id}")
    suspend fun deleteArtist(
        @Path("id") id: Int,
        @Query("deleteFiles") deleteFiles: Boolean? = null,
        @Query("addImportListExclusion") addImportListExclusion: Boolean? = null,
    ): JsonObject

    @GET("api/v1/artist")
    suspend fun getArtists(
        @Query("mbId") mbId: String? = null,
    ): List<ArtistResource>

    @POST("api/v1/artist")
    suspend fun addArtist(
        @Body body: ArtistResource,
    ): ArtistResource

}
