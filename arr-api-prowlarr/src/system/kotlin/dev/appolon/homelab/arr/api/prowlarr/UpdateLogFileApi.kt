package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.LogFileResource
import kotlin.jvm.JvmSuppressWildcards
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * UpdateLogFile tag: update log metadata plus contents. Contents are
 * `text/plain` on the wire, so they return a raw [ResponseBody] instead of
 * going through the JSON converter.
 */
@JvmSuppressWildcards
interface UpdateLogFileApi {
    @GET("api/v1/log/file/update")
    suspend fun getUpdateLogFiles(): List<LogFileResource>

    @GET("api/v1/log/file/update/{filename}")
    suspend fun getUpdateLogFileContents(@Path("filename") filename: String): ResponseBody
}
