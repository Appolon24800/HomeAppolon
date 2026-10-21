package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.IndexerCategory
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.GET

/**
 * IndexerDefaultCategories tag: the server-wide Newznab category tree that
 * indexer capability categories are drawn from.
 */
@JvmSuppressWildcards
interface IndexerDefaultCategoriesApi {
    @GET("api/v1/indexer/categories")
    suspend fun getCategories(): List<IndexerCategory>
}
