package dev.appolon.homelab.arr.api.bazarr

import kotlin.jvm.JvmSuppressWildcards
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET

/** Log entries have no schema in the bundled spec, so the payload stays raw JSON. */
@JvmSuppressWildcards
interface SystemLogsApi {
    @GET("api/system/logs")
    suspend fun getSystemLogs(): JsonObject

    @DELETE("api/system/logs")
    suspend fun deleteSystemLogs(): Response<Unit>
}
