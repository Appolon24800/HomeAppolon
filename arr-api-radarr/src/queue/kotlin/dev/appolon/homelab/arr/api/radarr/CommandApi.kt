package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.CommandResource
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Radarr endpoints tagged Command. */
interface CommandApi {
    @POST("api/v3/command")
    suspend fun startCommand(
        @Body body: CommandResource,
    ): Response<CommandResource>

    @GET("api/v3/command")
    @JvmSuppressWildcards
    suspend fun commands(): List<CommandResource>

    @DELETE("api/v3/command/{id}")
    suspend fun cancelCommand(
        @Path("id") id: Int,
    ): Response<Unit>

    @GET("api/v3/command/{id}")
    suspend fun command(
        @Path("id") id: Int,
    ): Response<CommandResource>

}
