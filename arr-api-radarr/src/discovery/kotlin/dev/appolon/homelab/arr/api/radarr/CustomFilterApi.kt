package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.CustomFilterResource
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** Radarr endpoints tagged CustomFilter. */
interface CustomFilterApi {
    @GET("api/v3/customfilter")
    suspend fun customFilters(): List<CustomFilterResource>

    @POST("api/v3/customfilter")
    suspend fun createCustomFilter(
        @Body body: CustomFilterResource,
    ): Response<CustomFilterResource>

    @PUT("api/v3/customfilter/{id}")
    suspend fun updateCustomFilter(
        @Path("id") id: String,
        @Body body: CustomFilterResource,
    ): Response<CustomFilterResource>

    @DELETE("api/v3/customfilter/{id}")
    suspend fun removeCustomFilter(
        @Path("id") id: Int,
    ): Response<Unit>

    @GET("api/v3/customfilter/{id}")
    suspend fun customFilter(
        @Path("id") id: Int,
    ): Response<CustomFilterResource>

}
