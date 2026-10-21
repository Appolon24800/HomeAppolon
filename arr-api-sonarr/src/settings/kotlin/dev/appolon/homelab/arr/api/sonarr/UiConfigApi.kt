package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.UiConfigResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

/** Operations in the Sonarr UiConfig OpenAPI tag. */
@JvmSuppressWildcards
interface UiConfigApi {
    @PUT("api/v3/config/ui/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body body: UiConfigResource
    ): UiConfigResource

    @GET("api/v3/config/ui/{id}")
    suspend fun get(@Path("id") id: Int): Response<UiConfigResource>

    @GET("api/v3/config/ui")
    suspend fun get(): UiConfigResource
}
