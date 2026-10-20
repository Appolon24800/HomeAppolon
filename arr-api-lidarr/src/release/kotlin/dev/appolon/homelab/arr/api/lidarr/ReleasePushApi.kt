package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.ReleaseResource
import retrofit2.http.Body
import retrofit2.http.POST

interface ReleasePushApi {
    @POST("api/v1/release/push")
    suspend fun pushRelease(@Body body: ReleaseResource): ReleaseResource
}
