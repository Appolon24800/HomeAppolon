package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.CommandResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Command tag: queue long-running server jobs and poll their status. POST
 * takes a [CommandResource] whose body carries the command name, per the
 * spec's request schema.
 */
@JvmSuppressWildcards
interface CommandApi {
    @GET("api/v1/command")
    suspend fun getCommands(): List<CommandResource>

    @GET("api/v1/command/{id}")
    suspend fun getCommand(@Path("id") id: Int): CommandResource

    @POST("api/v1/command")
    suspend fun createCommand(@Body resource: CommandResource): CommandResource

    @DELETE("api/v1/command/{id}")
    suspend fun deleteCommand(@Path("id") id: Int): Response<Unit>
}
