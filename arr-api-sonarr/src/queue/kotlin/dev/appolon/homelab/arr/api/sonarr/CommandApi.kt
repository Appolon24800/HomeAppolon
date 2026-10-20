package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.CommandResource
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Operations in the Sonarr Command OpenAPI tag. */
@JvmSuppressWildcards
interface CommandApi {
    @POST("api/v3/command")
    suspend fun create(
        @Body body: CommandResource
    ): CommandResource

    @GET("api/v3/command")
    suspend fun list(): List<CommandResource>

    @DELETE("api/v3/command/{id}")
    suspend fun delete(
        @Path("id") id: Int
    ): Unit

    @GET("api/v3/command/{id}")
    suspend fun get(
        @Path("id") id: Int
    ): Response<CommandResource>
}
