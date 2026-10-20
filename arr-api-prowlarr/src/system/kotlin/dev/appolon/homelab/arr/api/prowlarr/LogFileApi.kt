package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.LogFileResource
import kotlin.jvm.JvmSuppressWildcards
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * LogFile tag: log file metadata plus contents. Contents are `text/plain` on
 * the wire, so they return a raw [ResponseBody] instead of going through the
 * JSON converter.
 */
@JvmSuppressWildcards
interface LogFileApi {
    @GET("api/v1/log/file")
    suspend fun getLogFiles(): List<LogFileResource>

    @GET("api/v1/log/file/{filename}")
    suspend fun getLogFileContents(@Path("filename") filename: String): ResponseBody
}
