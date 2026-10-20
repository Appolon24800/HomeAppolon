package dev.appolon.homelab.arr.api.radarr.api

import dev.appolon.homelab.arr.api.radarr.model.MovieFileListResource
import dev.appolon.homelab.arr.api.radarr.model.MovieFileResource
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Radarr `MovieFile` tag: queries and edits the physical files behind movies. */
interface MovieFileApi {

    /**
     * Lists movie files. Both filters repeat their query name per value
     * (`?movieId=1&movieId=2`), matching ASP.NET collection binding; the
     * server answers 400 when neither filter is present.
     */
    @GET("api/v3/moviefile")
    suspend fun list(
        @Query("movieId") movieId: List<Int>? = null,
        @Query("movieFileIds") movieFileIds: List<Int>? = null,
    ): List<MovieFileResource>

    /** Fetches one movie file; 404 surfaces through [Response]. */
    @GET("api/v3/moviefile/{id}")
    suspend fun get(@Path("id") id: Int): Response<MovieFileResource>

    /** Updates one movie file; the path id is a string per the spec. 202 on success. */
    @PUT("api/v3/moviefile/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body movieFile: MovieFileResource,
    ): Response<MovieFileResource>

    /** Deletes one movie file; 404 surfaces through [Response]. */
    @DELETE("api/v3/moviefile/{id}")
    suspend fun delete(@Path("id") id: Int): Response<Unit>

    /** Replaces a batch of movie files wholesale, keyed by each body element's id. */
    @PUT("api/v3/moviefile/bulk")
    suspend fun updateBulk(@Body movieFiles: List<MovieFileResource>): List<MovieFileResource>

    /**
     * Deletes every listed movie file. Raw `@HTTP` form because the ids ride in
     * the request body and plain `@DELETE` rejects `@Body` methods.
     */
    @HTTP(method = "DELETE", path = "api/v3/moviefile/bulk", hasBody = true)
    suspend fun deleteBulk(@Body files: MovieFileListResource)

    /** Legacy single-patch bulk edit, superseded by [updateBulk] but still served. */
    @Deprecated("Radarr deprecated the moviefile editor endpoint; use updateBulk")
    @PUT("api/v3/moviefile/editor")
    suspend fun updateEditor(@Body files: MovieFileListResource): List<MovieFileResource>
}
