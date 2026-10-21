package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.CustomFilterResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Lidarr endpoints tagged CustomFilter. The update path id is a string in the
 * spec, unlike the integer ids on get and delete.
 */
@JvmSuppressWildcards
interface CustomFilterApi {
    @GET("api/v1/customfilter")
    suspend fun getCustomFilters(): List<CustomFilterResource>

    @GET("api/v1/customfilter/{id}")
    suspend fun getCustomFilter(@Path("id") id: Int): CustomFilterResource

    @POST("api/v1/customfilter")
    suspend fun addCustomFilter(
        @Body resource: CustomFilterResource,
    ): CustomFilterResource

    @PUT("api/v1/customfilter/{id}")
    suspend fun updateCustomFilter(
        @Path("id") id: String,
        @Body resource: CustomFilterResource,
    ): CustomFilterResource

    @DELETE("api/v1/customfilter/{id}")
    suspend fun deleteCustomFilter(@Path("id") id: Int)
}
