package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.IndexerFlagResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.GET

@JvmSuppressWildcards
interface IndexerFlagApi {
    @GET("api/v3/indexerflag")
    suspend fun list(): List<IndexerFlagResource>
}
