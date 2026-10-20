package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface SeriesApi {
    @GET("api/v3/series/lookup")
    suspend fun lookup(
        @Query("term") term: String? = null,
        @Query("language") language: Int? = null
    ): List<SeriesResource>

    @GET("api/v3/series")
    suspend fun list(
        @Query("tvdbId") tvdbId: Int? = null,
        @Query("includeSeasonImages") includeSeasonImages: Boolean? = null
    ): List<SeriesResource>

    @POST("api/v3/series")
    suspend fun create(
        @Body body: SeriesResource
    ): SeriesResource

    @GET("api/v3/series/{id}")
    suspend fun get(
        @Path("id") id: Int,
        @Query("includeSeasonImages") includeSeasonImages: Boolean? = null
    ): SeriesResource

    @PUT("api/v3/series/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Query("moveFiles") moveFiles: Boolean? = null,
        @Body body: SeriesResource
    ): SeriesResource

    @DELETE("api/v3/series/{id}")
    suspend fun delete(
        @Path("id") id: Int,
        @Query("deleteFiles") deleteFiles: Boolean? = null,
        @Query("addImportListExclusion") addImportListExclusion: Boolean? = null
    ): Unit
}
