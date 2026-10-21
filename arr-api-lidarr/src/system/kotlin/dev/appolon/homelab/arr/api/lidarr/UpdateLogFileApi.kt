package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.LogFileResource
import kotlin.jvm.JvmSuppressWildcards
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Lidarr endpoints tagged UpdateLogFile. Like LogFileApi, file content is raw
 * text and keeps the Response wrapper for 404 handling.
 */
@JvmSuppressWildcards
interface UpdateLogFileApi {
    @GET("api/v1/log/file/update")
    suspend fun getUpdateLogFiles(): List<LogFileResource>

    @GET("api/v1/log/file/update/{filename}")
    suspend fun getUpdateLogFile(@Path("filename") filename: String): Response<ResponseBody>
}
