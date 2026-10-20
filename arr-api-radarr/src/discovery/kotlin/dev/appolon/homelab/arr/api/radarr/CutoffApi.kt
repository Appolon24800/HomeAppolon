package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.MovieResourcePagingResource
import dev.appolon.homelab.arr.api.radarr.model.SortDirection
import retrofit2.http.GET
import retrofit2.http.Query

/** Radarr endpoints tagged Cutoff. */
interface CutoffApi {
    @GET("api/v3/wanted/cutoff")
    suspend fun cutoff(
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null,
        @Query("sortKey") sortKey: String? = null,
        @Query("sortDirection") sortDirection: SortDirection? = null,
        @Query("monitored") monitored: Boolean? = null,
    ): MovieResourcePagingResource

}
