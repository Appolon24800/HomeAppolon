package dev.appolon.homelab.arr.api.radarr.api

import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Radarr `Movie` tag. Paths are relative so the connection layer's
 * prefix-preserving base URL (`http://host:30025/radarr/`) resolves them
 * correctly.
 */
interface MovieApi {

    /** Lists all movies, optionally narrowed to one TMDB entry. */
    @GET("api/v3/movie")
    suspend fun list(
        @Query("tmdbId") tmdbId: Int? = null,
        @Query("excludeLocalCovers") excludeLocalCovers: Boolean? = null,
        @Query("languageId") languageId: Int? = null,
    ): List<MovieResource>

    /**
     * Fetches one movie. 404 means the id is gone, so the status is surfaced
     * through [Response] instead of an exception.
     */
    @GET("api/v3/movie/{id}")
    suspend fun get(@Path("id") id: Int): Response<MovieResource>

    /** Adds a movie; 201 on success, 404 when TMDB metadata is missing. */
    @POST("api/v3/movie")
    suspend fun create(@Body movie: MovieResource): Response<MovieResource>

    /**
     * Updates a movie. The path id is a string per the spec (Radarr accepts
     * both numeric ids and foreign-key forms here); 202 because a file move
     * may run afterwards.
     */
    @PUT("api/v3/movie/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Query("moveFiles") moveFiles: Boolean? = null,
        @Body movie: MovieResource,
    ): Response<MovieResource>

    @DELETE("api/v3/movie/{id}")
    suspend fun delete(
        @Path("id") id: Int,
        @Query("deleteFiles") deleteFiles: Boolean? = null,
        @Query("addImportExclusion") addImportExclusion: Boolean? = null,
    )
}
