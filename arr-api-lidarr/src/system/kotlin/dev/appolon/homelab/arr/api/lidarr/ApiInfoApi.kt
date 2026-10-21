package dev.appolon.homelab.arr.api.lidarr

import kotlin.jvm.JvmSuppressWildcards
import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET

/**
 * Lidarr endpoints tagged ApiInfo. The spec documents no response schema for
 * GET /api, so the API descriptor passes through as raw JSON.
 */
@JvmSuppressWildcards
interface ApiInfoApi {
    @GET("api")
    suspend fun getApiInfo(): JsonObject
}
