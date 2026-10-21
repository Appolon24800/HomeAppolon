package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.api.sonarr.model.EpisodesMonitoredResource
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface EpisodeApi {
    @GET("api/v3/episode")
    suspend fun list(
        @Query("seriesId") seriesId: Int? = null,
        @Query("seasonNumber") seasonNumber: Int? = null,
        @Query("episodeIds") episodeIds: List<Int>? = null,
        @Query("episodeFileId") episodeFileId: Int? = null,
        @Query("includeSeries") includeSeries: Boolean? = null,
        @Query("includeEpisodeFile") includeEpisodeFile: Boolean? = null,
        @Query("includeImages") includeImages: Boolean? = null
    ): List<EpisodeResource>

    @PUT("api/v3/episode/{id}")
    suspend fun update(
        @Path("id") id: Int,
        @Body body: EpisodeResource
    ): EpisodeResource

    @GET("api/v3/episode/{id}")
    suspend fun get(
        @Path("id") id: Int
    ): Response<EpisodeResource>

    @PUT("api/v3/episode/monitor")
    suspend fun updateMonitor(
        @Query("includeImages") includeImages: Boolean? = null,
        @Body body: EpisodesMonitoredResource
    ): Unit
}
