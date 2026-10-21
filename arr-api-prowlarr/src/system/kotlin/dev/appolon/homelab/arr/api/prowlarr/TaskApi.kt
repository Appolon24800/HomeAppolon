package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.TaskResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.GET
import retrofit2.http.Path

/** Task tag: the server's scheduled jobs shown on the system screen. */
@JvmSuppressWildcards
interface TaskApi {
    @GET("api/v1/system/task")
    suspend fun getTasks(): List<TaskResource>

    @GET("api/v1/system/task/{id}")
    suspend fun getTask(@Path("id") id: Int): TaskResource
}
