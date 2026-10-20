package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.CustomFilterResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * CustomFilter tag: CRUD on saved filter presets. The PUT path id is a
 * string because the spec declares that type on the update route.
 */
@JvmSuppressWildcards
interface CustomFilterApi {
    @GET("api/v1/customfilter")
    suspend fun getCustomFilters(): List<CustomFilterResource>

    @GET("api/v1/customfilter/{id}")
    suspend fun getCustomFilter(@Path("id") id: Int): CustomFilterResource

    @POST("api/v1/customfilter")
    suspend fun createCustomFilter(@Body resource: CustomFilterResource): CustomFilterResource

    @PUT("api/v1/customfilter/{id}")
    suspend fun updateCustomFilter(
        @Path("id") id: String,
        @Body resource: CustomFilterResource,
    ): CustomFilterResource

    @DELETE("api/v1/customfilter/{id}")
    suspend fun deleteCustomFilter(@Path("id") id: Int): Response<Unit>
}
