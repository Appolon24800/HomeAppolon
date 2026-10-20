package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.HealthResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.GET

/** Health tag: outstanding health checks shown on the system status screen. */
@JvmSuppressWildcards
interface HealthApi {
    @GET("api/v1/health")
    suspend fun getHealth(): List<HealthResource>
}
