package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.ReleaseResource
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ReleaseApi {
    @POST("api/v1/release")
    suspend fun grabRelease(@Body body: ReleaseResource): ReleaseResource

    @GET("api/v1/release")
    suspend fun getReleases(
        @Query("albumId") albumId: Int? = null,
        @Query("artistId") artistId: Int? = null,
    ): List<ReleaseResource>
}
