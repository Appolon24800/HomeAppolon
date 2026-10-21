package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.PingResource
import retrofit2.Response
import retrofit2.http.GET

/** Radarr endpoints tagged Ping. The 500 response body uses the same schema as 200. */
@JvmSuppressWildcards
interface PingApi {
    @GET("ping")
    suspend fun ping(): Response<PingResource>
}
