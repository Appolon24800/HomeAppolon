package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.IndexerFlagResource
import retrofit2.http.GET

/** Radarr endpoints tagged IndexerFlag. */
@JvmSuppressWildcards
interface IndexerFlagApi {
    @GET("api/v3/indexerflag")
    suspend fun indexerFlags(): List<IndexerFlagResource>

}
