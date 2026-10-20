package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.lidarr.model.QueueBulkResource
import dev.appolon.homelab.arr.api.lidarr.model.QueueResourcePagingResource
import dev.appolon.homelab.arr.api.lidarr.model.SortDirection
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Path
import retrofit2.http.Query

interface QueueApi {
    @GET("api/v1/queue")
    suspend fun getQueue(
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null,
        @Query("sortKey") sortKey: String? = null,
        @Query("sortDirection") sortDirection: SortDirection? = null,
        @Query("includeUnknownArtistItems") includeUnknownArtistItems: Boolean? = null,
        @Query("includeArtist") includeArtist: Boolean? = null,
        @Query("includeAlbum") includeAlbum: Boolean? = null,
        @Query("artistIds") artistIds: List<Int>? = null,
        @Query("protocol") protocol: DownloadProtocol? = null,
        @Query("quality") quality: List<Int>? = null,
    ): QueueResourcePagingResource

    @DELETE("api/v1/queue/{id}")
    suspend fun deleteQueue(
        @Path("id") id: Int,
        @Query("removeFromClient") removeFromClient: Boolean? = null,
        @Query("blocklist") blocklist: Boolean? = null,
        @Query("skipRedownload") skipRedownload: Boolean? = null,
        @Query("changeCategory") changeCategory: Boolean? = null,
    )

    @HTTP(method = "DELETE", path = "api/v1/queue/bulk", hasBody = true)
    suspend fun deleteQueueBulk(
        @Body body: QueueBulkResource,
        @Query("removeFromClient") removeFromClient: Boolean? = null,
        @Query("blocklist") blocklist: Boolean? = null,
        @Query("skipRedownload") skipRedownload: Boolean? = null,
        @Query("changeCategory") changeCategory: Boolean? = null,
    )
}
