package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.SystemRestartResource
import dev.appolon.homelab.arr.api.radarr.model.SystemResource
import dev.appolon.homelab.arr.api.radarr.model.SystemShutdownResource
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Radarr endpoints tagged System. `/routes` is `text/plain` on the wire, so it
 * returns a raw [ResponseBody]; `/routes/duplicate` is a JSON object mapping
 * route templates to duplicated HTTP methods.
 */
@JvmSuppressWildcards
interface SystemApi {
    @GET("api/v3/system/status")
    suspend fun systemStatus(): SystemResource

    @GET("api/v3/system/routes")
    suspend fun systemRoutes(): ResponseBody

    @GET("api/v3/system/routes/duplicate")
    suspend fun systemRoutesDuplicate(): Map<String, List<String>>

    @POST("api/v3/system/shutdown")
    suspend fun shutdown(): SystemShutdownResource

    @POST("api/v3/system/restart")
    suspend fun restart(): SystemRestartResource
}
