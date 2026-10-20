package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.TaskResource
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/** Radarr endpoints tagged Task. */
@JvmSuppressWildcards
interface TaskApi {
    @GET("api/v3/system/task")
    suspend fun tasks(): List<TaskResource>

    @GET("api/v3/system/task/{id}")
    suspend fun task(
        @Path("id") id: Int,
    ): Response<TaskResource>
}
