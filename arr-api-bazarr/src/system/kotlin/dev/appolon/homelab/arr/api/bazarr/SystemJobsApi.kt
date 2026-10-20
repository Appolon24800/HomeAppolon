package dev.appolon.homelab.arr.api.bazarr

import kotlin.jvm.JvmSuppressWildcards
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Job payloads have no schema in the bundled spec, so reads stay raw JSON.
 * Spec enumerates [action] as one of force_start, move_top, move_bottom and [status] as one of
 * pending, running, failed, completed, but both travel as plain strings to match the wire form.
 */
@JvmSuppressWildcards
interface SystemJobsApi {
    @GET("system/jobs")
    suspend fun getSystemJobs(
        @Query("id") id: Int? = null,
        @Query("status") status: String? = null,
    ): JsonObject

    @POST("system/jobs")
    suspend fun postSystemJobs(
        @Query("id") id: Int,
        @Query("action") action: String,
    ): Response<Unit>

    @PATCH("system/jobs")
    suspend fun patchSystemJobs(@Query("queueName") queueName: String): Response<Unit>

    @DELETE("system/jobs")
    suspend fun deleteSystemJobs(@Query("id") id: Int): Response<Unit>
}
