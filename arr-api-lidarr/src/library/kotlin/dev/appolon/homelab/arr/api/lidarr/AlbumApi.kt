package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.AlbumsMonitoredResource
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Album endpoints from the checked-in Lidarr specification. */
interface AlbumApi {
    @GET("api/v1/album")
    suspend fun getAlbums(
        @Query("artistId") artistId: Int? = null,
        @Query("albumIds") albumIds: List<Int>? = null,
        @Query("foreignAlbumId") foreignAlbumId: String? = null,
        @Query("includeAllArtistAlbums") includeAllArtistAlbums: Boolean? = null,
    ): List<AlbumResource>

    @POST("api/v1/album")
    suspend fun addAlbum(
        @Body body: AlbumResource,
    ): AlbumResource

    @PUT("api/v1/album/{id}")
    suspend fun updateAlbum(
        @Path("id") id: String,
        @Body body: AlbumResource,
    ): AlbumResource

    /** The specification omits the response schema. */
    @DELETE("api/v1/album/{id}")
    suspend fun deleteAlbum(
        @Path("id") id: Int,
        @Query("deleteFiles") deleteFiles: Boolean? = null,
        @Query("addImportListExclusion") addImportListExclusion: Boolean? = null,
    ): JsonObject

    @GET("api/v1/album/{id}")
    suspend fun getAlbum(
        @Path("id") id: Int,
    ): AlbumResource

    /** The specification omits the response schema. */
    @PUT("api/v1/album/monitor")
    suspend fun monitorAlbums(
        @Body body: AlbumsMonitoredResource,
    ): JsonObject

}
