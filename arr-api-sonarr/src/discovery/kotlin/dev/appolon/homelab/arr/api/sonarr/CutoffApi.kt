package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResourcePagingResource
import dev.appolon.homelab.arr.api.sonarr.model.SortDirection
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

@JvmSuppressWildcards
interface CutoffApi {
    @GET("api/v3/wanted/cutoff")
    suspend fun list(
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null,
        @Query("sortKey") sortKey: String? = null,
        @Query("sortDirection") sortDirection: SortDirection? = null,
        @Query("includeSeries") includeSeries: Boolean? = null,
        @Query("includeEpisodeFile") includeEpisodeFile: Boolean? = null,
        @Query("includeImages") includeImages: Boolean? = null,
        @Query("monitored") monitored: Boolean? = null
    ): EpisodeResourcePagingResource

    @GET("api/v3/wanted/cutoff/{id}")
    suspend fun get(
        @Path("id") id: Int
    ): Response<EpisodeResource>
}
