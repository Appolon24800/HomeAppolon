package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.AlbumResourcePagingResource
import dev.appolon.homelab.arr.api.lidarr.model.SortDirection
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Lidarr endpoints tagged Missing for albums with no track files. */
@JvmSuppressWildcards
interface MissingApi {
    @GET("api/v1/wanted/missing")
    suspend fun getMissing(
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null,
        @Query("sortKey") sortKey: String? = null,
        @Query("sortDirection") sortDirection: SortDirection? = null,
        @Query("includeArtist") includeArtist: Boolean? = null,
        @Query("monitored") monitored: Boolean? = null,
    ): AlbumResourcePagingResource

    @GET("api/v1/wanted/missing/{id}")
    suspend fun getMissingById(@Path("id") id: Int): AlbumResource
}
