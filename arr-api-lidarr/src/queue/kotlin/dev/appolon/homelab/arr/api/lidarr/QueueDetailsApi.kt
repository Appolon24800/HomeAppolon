package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.QueueResource
import retrofit2.http.GET
import retrofit2.http.Query

interface QueueDetailsApi {
    @GET("api/v1/queue/details")
    suspend fun getQueueDetails(
        @Query("artistId") artistId: Int? = null,
        @Query("albumIds") albumIds: List<Int>? = null,
        @Query("includeArtist") includeArtist: Boolean? = null,
        @Query("includeAlbum") includeAlbum: Boolean? = null,
    ): List<QueueResource>
}
