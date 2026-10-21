package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.LogFileResource
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Radarr endpoints tagged LogFile. File contents are `text/plain` on the wire,
 * so they return as a raw [ResponseBody] instead of going through the JSON
 * converter.
 */
@JvmSuppressWildcards
interface LogFileApi {
    @GET("api/v3/log/file")
    suspend fun logFiles(): List<LogFileResource>

    @GET("api/v3/log/file/{filename}")
    suspend fun logFileContents(
        @Path("filename") filename: String,
    ): ResponseBody
}
