package dev.appolon.homelab.arr.api.prowlarr

import kotlin.jvm.JvmSuppressWildcards
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

/**
 * Authentication tag: forms-login sessions for servers that require them.
 * The spec posts multipart form data, so the parts are raw [RequestBody]s;
 * the JSON converter would quote string parts and the server would bind the
 * quotes verbatim. A null [login.rememberMe] part is omitted, matching the
 * optional form field. Both calls return [Response] because the status code
 * is the whole result.
 */
@JvmSuppressWildcards
interface AuthenticationApi {
    @Multipart
    @POST("login")
    suspend fun login(
        @Part("username") username: RequestBody,
        @Part("password") password: RequestBody,
        @Part("rememberMe") rememberMe: RequestBody? = null,
        @Query("returnUrl") returnUrl: String? = null,
    ): Response<Unit>

    @GET("logout")
    suspend fun logout(): Response<Unit>
}
