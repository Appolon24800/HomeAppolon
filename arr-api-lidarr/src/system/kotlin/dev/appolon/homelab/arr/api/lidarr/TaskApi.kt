package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.TaskResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.GET
import retrofit2.http.Path

/** Lidarr endpoints tagged Task for the scheduled job list. */
@JvmSuppressWildcards
interface TaskApi {
    @GET("api/v1/system/task")
    suspend fun getTasks(): List<TaskResource>

    @GET("api/v1/system/task/{id}")
    suspend fun getTask(@Path("id") id: Int): TaskResource
}
