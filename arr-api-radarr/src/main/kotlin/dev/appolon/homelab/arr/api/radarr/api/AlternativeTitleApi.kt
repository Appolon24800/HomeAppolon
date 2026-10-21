package dev.appolon.homelab.arr.api.radarr.api

import dev.appolon.homelab.arr.api.radarr.model.AlternativeTitleResource
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Radarr `AlternativeTitle` tag: alternate titles attached to movie metadata. */
interface AlternativeTitleApi {

    @GET("api/v3/alttitle")
    suspend fun list(
        @Query("movieId") movieId: Int? = null,
        @Query("movieMetadataId") movieMetadataId: Int? = null,
    ): List<AlternativeTitleResource>

    /** Fetches one title; 404 surfaces through [Response]. */
    @GET("api/v3/alttitle/{id}")
    suspend fun get(@Path("id") id: Int): Response<AlternativeTitleResource>
}
