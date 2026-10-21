package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.SearchResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.GET
import retrofit2.http.Query

/** Lidarr endpoints tagged Search for artist and album lookup. */
@JvmSuppressWildcards
interface SearchApi {
    @GET("api/v1/search")
    suspend fun search(@Query("term") term: String? = null): List<SearchResource>
}
