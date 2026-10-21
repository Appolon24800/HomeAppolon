package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

/**
 * Radarr endpoints tagged Authentication. These are the cookie-based UI login
 * routes, not the `X-Api-Key` header the rest of the module uses, so they only
 * matter when a server demands forms auth before the API key works. The spec
 * documents no success body (302 redirects), so only the status is observed.
 */
@JvmSuppressWildcards
interface AuthenticationApi {
    @GET("login/sso")
    suspend fun loginSso(
        @Query("returnUrl") returnUrl: String? = null,
    ): Response<Unit>

    /** Form login; parts follow the spec's multipart schema (username, password, rememberMe). */
    @Multipart
    @POST("login")
    suspend fun login(
        @Query("returnUrl") returnUrl: String? = null,
        @Part("username") username: RequestBody,
        @Part("password") password: RequestBody,
        @Part("rememberMe") rememberMe: RequestBody? = null,
    ): Response<Unit>

    @GET("logout")
    suspend fun logout(): Response<Unit>
}
