package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.CommandResource
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface CommandApi {
    @GET("api/v1/command/{id}")
    suspend fun getCommand(@Path("id") id: Int): CommandResource

    @DELETE("api/v1/command/{id}")
    suspend fun deleteCommand(@Path("id") id: Int)

    @POST("api/v1/command")
    suspend fun addCommand(@Body body: CommandResource): CommandResource

    @GET("api/v1/command")
    suspend fun getCommands(): List<CommandResource>
}
