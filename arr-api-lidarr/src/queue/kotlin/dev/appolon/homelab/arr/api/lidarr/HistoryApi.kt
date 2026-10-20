package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.EntityHistoryEventType
import dev.appolon.homelab.arr.api.lidarr.model.HistoryResource
import dev.appolon.homelab.arr.api.lidarr.model.HistoryResourcePagingResource
import dev.appolon.homelab.arr.api.lidarr.model.SortDirection
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface HistoryApi {
    @GET("api/v1/history")
    suspend fun getHistory(
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null,
        @Query("sortKey") sortKey: String? = null,
        @Query("sortDirection") sortDirection: SortDirection? = null,
        @Query("includeArtist") includeArtist: Boolean? = null,
        @Query("includeAlbum") includeAlbum: Boolean? = null,
        @Query("includeTrack") includeTrack: Boolean? = null,
        @Query("eventType") eventType: List<Int>? = null,
        @Query("albumId") albumId: Int? = null,
        @Query("downloadId") downloadId: String? = null,
        @Query("artistIds") artistIds: List<Int>? = null,
        @Query("quality") quality: List<Int>? = null,
    ): HistoryResourcePagingResource

    @GET("api/v1/history/since")
    suspend fun getHistorySince(
        @Query("date") date: String? = null,
        @Query("eventType") eventType: EntityHistoryEventType? = null,
        @Query("includeArtist") includeArtist: Boolean? = null,
        @Query("includeAlbum") includeAlbum: Boolean? = null,
        @Query("includeTrack") includeTrack: Boolean? = null,
    ): List<HistoryResource>

    @GET("api/v1/history/artist")
    suspend fun getArtistHistory(
        @Query("artistId") artistId: Int? = null,
        @Query("albumId") albumId: Int? = null,
        @Query("eventType") eventType: EntityHistoryEventType? = null,
        @Query("includeArtist") includeArtist: Boolean? = null,
        @Query("includeAlbum") includeAlbum: Boolean? = null,
        @Query("includeTrack") includeTrack: Boolean? = null,
    ): List<HistoryResource>

    @POST("api/v1/history/failed/{id}")
    suspend fun failHistory(@Path("id") id: Int)
}
