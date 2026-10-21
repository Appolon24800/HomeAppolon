package dev.appolon.homelab.arr.api.prowlarr

import kotlin.jvm.JvmSuppressWildcards
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * StaticResource tag: the server's web UI pages and static assets, returned
 * as [Response] holding a raw [ResponseBody] because these routes answer
 * with HTML or binaries, redirect when unauthenticated, and never JSON.
 * Native screens use them only for deep links like authenticating a session
 * cookie or fetching an icon.
 */
@JvmSuppressWildcards
interface StaticResourceApi {
    @GET("login")
    suspend fun getLoginPage(): Response<ResponseBody>

    @GET("content/{path}")
    suspend fun getContent(@Path("path") path: String): Response<ResponseBody>

    /** The spec models `/` with a template `path` parameter that never binds; `./` targets the base. */
    @GET("./")
    suspend fun getRoot(): Response<ResponseBody>

    /** One path segment, per the spec pattern that excludes `api/` routes. */
    @GET("{path}")
    suspend fun getStaticResource(@Path("path") path: String): Response<ResponseBody>
}
