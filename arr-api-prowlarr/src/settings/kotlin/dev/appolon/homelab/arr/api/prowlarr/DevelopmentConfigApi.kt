package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.DevelopmentConfigResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * DevelopmentConfig tag: the development settings singleton. GET with any id
 * returns the one row; PUT path ids are strings because the spec declares
 * that type on update routes.
 */
@JvmSuppressWildcards
interface DevelopmentConfigApi {
    @GET("api/v1/config/development")
    suspend fun getDevelopmentConfig(): DevelopmentConfigResource

    @GET("api/v1/config/development/{id}")
    suspend fun getDevelopmentConfig(@Path("id") id: Int): DevelopmentConfigResource

    @PUT("api/v1/config/development/{id}")
    suspend fun updateDevelopmentConfig(
        @Path("id") id: String,
        @Body resource: DevelopmentConfigResource,
    ): DevelopmentConfigResource
}
