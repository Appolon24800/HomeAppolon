package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.ReleaseResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Search tag: the interactive cross-indexer search behind the search screen,
 * plus the grab endpoints that push one or many releases to the download
 * clients configured in Prowlarr.
 */
@JvmSuppressWildcards
interface SearchApi {
    @GET("api/v1/search")
    suspend fun getSearch(
        @Query("query") query: String?,
        @Query("type") type: String?,
        @Query("indexerIds") indexerIds: List<Int>?,
        @Query("categories") categories: List<Int>?,
        @Query("limit") limit: Int?,
        @Query("offset") offset: Int?,
    ): List<ReleaseResource>

    @POST("api/v1/search")
    suspend fun createSearch(@Body resource: ReleaseResource): ReleaseResource

    @POST("api/v1/search/bulk")
    suspend fun createSearchBulk(@Body resources: List<ReleaseResource>): ReleaseResource
}
