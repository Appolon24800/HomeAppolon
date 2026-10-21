package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.HealthResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.GET

/** Lidarr endpoints tagged Health. */
@JvmSuppressWildcards
interface HealthApi {
    @GET("api/v1/health")
    suspend fun getHealth(): List<HealthResource>
}
