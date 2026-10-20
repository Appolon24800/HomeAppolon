package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.ParseResource
import retrofit2.http.GET
import retrofit2.http.Query

interface ParseApi {
    @GET("api/v1/parse")
    suspend fun parse(@Query("title") title: String? = null): ParseResource
}
