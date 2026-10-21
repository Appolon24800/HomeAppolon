package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.UiConfigResource
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

/** Radarr endpoints tagged UiConfig. */
@JvmSuppressWildcards
interface UiConfigApi {
    /** The spec declares this path id as a string. */
    @PUT("api/v3/config/ui/{id}")
    suspend fun updateUiConfig(
        @Path("id") id: String,
        @Body body: UiConfigResource,
    ): Response<UiConfigResource>

    @GET("api/v3/config/ui/{id}")
    suspend fun uiConfigById(
        @Path("id") id: Int,
    ): Response<UiConfigResource>

    @GET("api/v3/config/ui")
    suspend fun uiConfig(): UiConfigResource
}
