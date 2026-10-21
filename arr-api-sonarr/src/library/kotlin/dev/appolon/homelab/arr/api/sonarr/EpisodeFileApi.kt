package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.EpisodeFileListResource
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeFileResource
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface EpisodeFileApi {
    @GET("api/v3/episodefile")
    suspend fun list(
        @Query("seriesId") seriesId: Int? = null,
        @Query("episodeFileIds") episodeFileIds: List<Int>? = null
    ): List<EpisodeFileResource>

    @PUT("api/v3/episodefile/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body body: EpisodeFileResource
    ): EpisodeFileResource

    @DELETE("api/v3/episodefile/{id}")
    suspend fun delete(
        @Path("id") id: Int
    ): Unit

    @GET("api/v3/episodefile/{id}")
    suspend fun get(
        @Path("id") id: Int
    ): Response<EpisodeFileResource>

    @Deprecated("Deprecated by Sonarr; use updateBulk instead")
    @PUT("api/v3/episodefile/editor")
    suspend fun updateEditor(
        @Body body: EpisodeFileListResource
    ): Unit

    @HTTP(method = "DELETE", path = "api/v3/episodefile/bulk", hasBody = true)
    suspend fun deleteBulk(
        @Body body: EpisodeFileListResource
    ): Unit

    @PUT("api/v3/episodefile/bulk")
    suspend fun updateBulk(
        @Body body: List<EpisodeFileResource>
    ): Unit
}
