package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.IndexerFlagResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.GET

/** Lidarr endpoints tagged IndexerFlag. */
@JvmSuppressWildcards
interface IndexerFlagApi {
    @GET("api/v1/indexerflag")
    suspend fun getIndexerFlags(): List<IndexerFlagResource>
}
