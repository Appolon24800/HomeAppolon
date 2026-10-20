package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.TaskResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/** Operations in the Sonarr Task OpenAPI tag. */
@JvmSuppressWildcards
interface TaskApi {
    @GET("api/v3/system/task")
    suspend fun list(): List<TaskResource>

    @GET("api/v3/system/task/{id}")
    suspend fun get(@Path("id") id: Int): Response<TaskResource>
}
