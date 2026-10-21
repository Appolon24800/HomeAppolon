package dev.appolon.homelab.arr.api.sonarr

import kotlin.jvm.JvmSuppressWildcards
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Operations in the Sonarr Authentication OpenAPI tag. The form endpoints take
 * an [MultipartBody] because the spec declares their body only as a bare
 * multipart object without named parts. Every method keeps [Response]
 * because the documented success is a 302 redirect and authentication
 * failures use other statuses.
 */
@JvmSuppressWildcards
interface AuthenticationApi {
    @GET("login/sso")
    suspend fun sso(@Query("returnUrl") returnUrl: String? = null): Response<Unit>

    @POST("login")
    suspend fun login(
        @Query("returnUrl") returnUrl: String? = null,
        @Body body: MultipartBody
    ): Response<Unit>

    @POST("reset-password/request")
    suspend fun requestPasswordReset(): Response<Unit>

    @POST("reset-password")
    suspend fun resetPassword(@Body body: MultipartBody): Response<Unit>

    @GET("logout")
    suspend fun logout(): Response<Unit>
}
