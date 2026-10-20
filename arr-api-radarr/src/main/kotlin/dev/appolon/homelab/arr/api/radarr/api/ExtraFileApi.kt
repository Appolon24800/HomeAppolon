package dev.appolon.homelab.arr.api.radarr.api

import dev.appolon.homelab.arr.api.radarr.model.ExtraFileResource
import retrofit2.http.GET
import retrofit2.http.Query

/** Radarr `ExtraFile` tag: sidecar files such as subtitles next to a movie file. */
interface ExtraFileApi {

    @GET("api/v3/extrafile")
    suspend fun list(@Query("movieId") movieId: Int? = null): List<ExtraFileResource>
}
