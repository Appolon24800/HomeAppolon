package dev.appolon.homelab.arr.api.radarr.api

import dev.appolon.homelab.arr.api.radarr.model.CreditResource
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Radarr `Credit` tag: cast and crew credits attached to movie metadata. */
interface CreditApi {

    @GET("api/v3/credit")
    suspend fun list(
        @Query("movieId") movieId: Int? = null,
        @Query("movieMetadataId") movieMetadataId: Int? = null,
    ): List<CreditResource>

    /** Fetches one credit; 404 surfaces through [Response]. */
    @GET("api/v3/credit/{id}")
    suspend fun get(@Path("id") id: Int): Response<CreditResource>
}
