package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.LogFileResource
import kotlin.jvm.JvmSuppressWildcards
import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Operations in the Sonarr LogFile OpenAPI tag. The spec declares no response
 * schema for the contents endpoint, so the body passes through as raw JSON.
 */
@JvmSuppressWildcards
interface LogFileApi {
    @GET("api/v3/log/file")
    suspend fun list(): List<LogFileResource>

    @GET("api/v3/log/file/{filename}")
    suspend fun get(@Path("filename") filename: String): JsonObject
}
