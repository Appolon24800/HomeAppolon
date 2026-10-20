package dev.appolon.homelab.arr.api.lidarr

import kotlin.jvm.JvmSuppressWildcards
import okhttp3.RequestBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

/**
 * Lidarr endpoints tagged Authentication. The connection layer in :arr-data
 * normally authenticates with the X-Api-Key header; these form endpoints back
 * the username and password setup flow instead.
 */
@JvmSuppressWildcards
interface AuthenticationApi {
    /** The spec types the form fields as plain strings inside multipart data. */
    @Multipart
    @POST("login")
    suspend fun login(
        @Part("username") username: RequestBody,
        @Part("password") password: RequestBody,
        @Part("rememberMe") rememberMe: RequestBody?,
        @Query("returnUrl") returnUrl: String? = null,
    )

    @GET("logout")
    suspend fun logout()
}
