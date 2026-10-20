package dev.appolon.homelab.arr.api.lidarr

import kotlin.jvm.JvmSuppressWildcards
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Lidarr endpoints tagged StaticResource, serving the bundled web UI. Bodies are
 * passthrough bytes because the spec declares no content schema. The status code
 * carries meaning for cacheable assets (404, 304), so these return [Response].
 *
 * The spec also declares a required path parameter on the root operation, which
 * has no {path} placeholder to bind; [getStaticResource] covers that parameter.
 * The root itself resolves relative to the base URL, so it is "./" rather than
 * the empty string Retrofit rejects.
 */
@JvmSuppressWildcards
interface StaticResourceApi {
    @GET("login")
    suspend fun getLoginPage(): Response<ResponseBody>

    @GET("content/{path}")
    suspend fun getContent(@Path("path") path: String): Response<ResponseBody>

    @GET("./")
    suspend fun getRoot(): Response<ResponseBody>

    @GET("{path}")
    suspend fun getStaticResource(@Path("path") path: String): Response<ResponseBody>
}
