package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.CollectionResource
import dev.appolon.homelab.arr.api.radarr.model.CollectionUpdateResource
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Radarr endpoints tagged Collection. */
interface CollectionApi {
    @GET("api/v3/collection")
    suspend fun collections(
        @Query("tmdbId") tmdbId: Int? = null,
    ): List<CollectionResource>

    @PUT("api/v3/collection")
    suspend fun updateCollections(
        @Body body: CollectionUpdateResource,
    ): List<CollectionResource>

    @PUT("api/v3/collection/{id}")
    suspend fun updateCollection(
        @Path("id") id: String,
        @Body body: CollectionResource,
    ): Response<CollectionResource>

    @GET("api/v3/collection/{id}")
    suspend fun collection(
        @Path("id") id: Int,
    ): Response<CollectionResource>

}
