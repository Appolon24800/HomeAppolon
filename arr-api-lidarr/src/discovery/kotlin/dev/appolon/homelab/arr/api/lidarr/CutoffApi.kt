package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.AlbumResourcePagingResource
import dev.appolon.homelab.arr.api.lidarr.model.SortDirection
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Lidarr endpoints tagged Cutoff for albums below the quality cutoff. */
@JvmSuppressWildcards
interface CutoffApi {
    @GET("api/v1/wanted/cutoff")
    suspend fun getCutoff(
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null,
        @Query("sortKey") sortKey: String? = null,
        @Query("sortDirection") sortDirection: SortDirection? = null,
        @Query("includeArtist") includeArtist: Boolean? = null,
        @Query("monitored") monitored: Boolean? = null,
    ): AlbumResourcePagingResource

    @GET("api/v1/wanted/cutoff/{id}")
    suspend fun getCutoffById(@Path("id") id: Int): AlbumResource
}
