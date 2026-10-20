package dev.appolon.homelab.arr.api.bazarr

import kotlin.jvm.JvmSuppressWildcards
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** Task listings have no schema in the bundled spec, so reads stay raw JSON. */
@JvmSuppressWildcards
interface SystemTasksApi {
    @GET("api/system/tasks")
    suspend fun getSystemTasks(): JsonObject

    @POST("api/system/tasks")
    suspend fun postSystemTasks(@Query("taskid") taskId: String): Response<Unit>
}
