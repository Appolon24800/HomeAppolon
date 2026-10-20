package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.CustomFilterResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

@JvmSuppressWildcards
interface CustomFilterApi {
    @GET("api/v3/customfilter")
    suspend fun list(): List<CustomFilterResource>

    @POST("api/v3/customfilter")
    suspend fun create(
        @Body body: CustomFilterResource
    ): CustomFilterResource

    @PUT("api/v3/customfilter/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body body: CustomFilterResource
    ): CustomFilterResource

    @DELETE("api/v3/customfilter/{id}")
    suspend fun delete(
        @Path("id") id: Int
    ): Unit

    @GET("api/v3/customfilter/{id}")
    suspend fun get(
        @Path("id") id: Int
    ): Response<CustomFilterResource>
}
