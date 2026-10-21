package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.UiConfigResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * UiConfig tag: the UI settings singleton. GET with any id returns the one
 * row; PUT path ids are strings because the spec declares that type on
 * update routes.
 */
@JvmSuppressWildcards
interface UiConfigApi {
    @GET("api/v1/config/ui")
    suspend fun getUiConfig(): UiConfigResource

    @GET("api/v1/config/ui/{id}")
    suspend fun getUiConfig(@Path("id") id: Int): UiConfigResource

    @PUT("api/v1/config/ui/{id}")
    suspend fun updateUiConfig(
        @Path("id") id: String,
        @Body resource: UiConfigResource,
    ): UiConfigResource
}
