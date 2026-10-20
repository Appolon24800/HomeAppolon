package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.UiConfigResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Lidarr endpoints tagged UiConfig. The update path id is a string in the
 * spec, unlike the integer id on the single get.
 */
@JvmSuppressWildcards
interface UiConfigApi {
    @GET("api/v1/config/ui")
    suspend fun getUiConfig(): UiConfigResource

    @GET("api/v1/config/ui/{id}")
    suspend fun getUiConfigById(@Path("id") id: Int): UiConfigResource

    @PUT("api/v1/config/ui/{id}")
    suspend fun updateUiConfig(
        @Path("id") id: String,
        @Body resource: UiConfigResource,
    ): UiConfigResource
}
