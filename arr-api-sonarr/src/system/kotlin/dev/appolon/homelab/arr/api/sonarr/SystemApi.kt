package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.SystemResource
import kotlin.jvm.JvmSuppressWildcards
import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Operations in the Sonarr System OpenAPI tag. The spec declares no response
 * schema for the route dumps, so both pass through as raw JSON.
 */
@JvmSuppressWildcards
interface SystemApi {
    @GET("api/v3/system/status")
    suspend fun status(): SystemResource

    @GET("api/v3/system/routes")
    suspend fun routes(): JsonObject

    @GET("api/v3/system/routes/duplicate")
    suspend fun routesDuplicate(): JsonObject

    @POST("api/v3/system/shutdown")
    suspend fun shutdown(): Unit

    @POST("api/v3/system/restart")
    suspend fun restart(): Unit
}
