package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.IndexerStatusResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.GET

/**
 * IndexerStatus tag: cooldown and failure state for every indexer, used by the
 * indexers screen to show disabled-until badges.
 */
@JvmSuppressWildcards
interface IndexerStatusApi {
    @GET("api/v1/indexerstatus")
    suspend fun getIndexerStatuses(): List<IndexerStatusResource>
}
