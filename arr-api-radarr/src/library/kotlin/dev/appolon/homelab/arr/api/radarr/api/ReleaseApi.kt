package dev.appolon.homelab.arr.api.radarr.api

import dev.appolon.homelab.arr.api.radarr.model.ReleaseResource
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ReleaseApi {
    @GET("api/v3/release")
    suspend fun list(@Query("movieId") movieId: Int? = null): List<ReleaseResource>

    @POST("api/v3/release")
    suspend fun grab(@Body release: ReleaseResource): Response<ReleaseResource>

    @POST("api/v3/release/push")
    suspend fun push(@Body release: ReleaseResource): List<ReleaseResource>
}
