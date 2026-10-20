package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.LogFileResource
import kotlin.jvm.JvmSuppressWildcards
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Lidarr endpoints tagged LogFile. The file content endpoint returns raw text,
 * so it keeps the Response wrapper: a missing filename surfaces as a 404 the
 * caller renders instead of an exception mid-stream.
 */
@JvmSuppressWildcards
interface LogFileApi {
    @GET("api/v1/log/file")
    suspend fun getLogFiles(): List<LogFileResource>

    @GET("api/v1/log/file/{filename}")
    suspend fun getLogFile(@Path("filename") filename: String): Response<ResponseBody>
}
