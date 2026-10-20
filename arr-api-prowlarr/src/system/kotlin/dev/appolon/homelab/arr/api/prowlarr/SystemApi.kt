package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.SystemResource
import kotlin.jvm.JvmSuppressWildcards
import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * System tag: server self-description and lifecycle controls. The spec ships
 * no response schema for the route dump endpoints, so `routes` returns a raw
 * [ResponseBody] (text dump on the wire) and `routes/duplicate` a
 * [JsonObject] passthrough. Shutdown and restart return [Response] because
 * acceptance is the whole result.
 */
@JvmSuppressWildcards
interface SystemApi {
    @GET("api/v1/system/status")
    suspend fun getSystemStatus(): SystemResource

    @GET("api/v1/system/routes")
    suspend fun getSystemRoutes(): ResponseBody

    @GET("api/v1/system/routes/duplicate")
    suspend fun getSystemRoutesDuplicate(): JsonObject

    @POST("api/v1/system/shutdown")
    suspend fun shutdown(): Response<Unit>

    @POST("api/v1/system/restart")
    suspend fun restart(): Response<Unit>
}
