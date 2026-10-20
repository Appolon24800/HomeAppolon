package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.LogFileResource
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Radarr endpoints tagged UpdateLogFile. File contents are `text/plain` on the
 * wire, so they return a raw [ResponseBody] instead of going through the JSON
 * converter.
 */
@JvmSuppressWildcards
interface UpdateLogFileApi {
    @GET("api/v3/log/file/update")
    suspend fun updateLogFiles(): List<LogFileResource>

    @GET("api/v3/log/file/update/{filename}")
    suspend fun updateLogFileContents(
        @Path("filename") filename: String,
    ): ResponseBody
}
