package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.SystemResource
import kotlin.jvm.JvmSuppressWildcards
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Lidarr endpoints tagged System. The route dump endpoints have no documented
 * response schema, so both pass through as raw JSON; the duplicate report is
 * an array on the wire, hence JsonElement instead of JsonObject.
 */
@JvmSuppressWildcards
interface SystemApi {
    @GET("api/v1/system/status")
    suspend fun getStatus(): SystemResource

    @GET("api/v1/system/routes")
    suspend fun getRoutes(): JsonObject

    @GET("api/v1/system/routes/duplicate")
    suspend fun getDuplicateRoutes(): JsonElement

    @POST("api/v1/system/restart")
    suspend fun restart()

    @POST("api/v1/system/shutdown")
    suspend fun shutdown()
}
