package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.HealthResource
import retrofit2.http.GET

/** Radarr endpoints tagged Health. */
@JvmSuppressWildcards
interface HealthApi {
    @GET("api/v3/health")
    suspend fun health(): List<HealthResource>
}
