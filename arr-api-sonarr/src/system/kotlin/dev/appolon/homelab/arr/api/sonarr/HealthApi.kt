package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.HealthResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.GET

/** Operations in the Sonarr Health OpenAPI tag. */
@JvmSuppressWildcards
interface HealthApi {
    @GET("api/v3/health")
    suspend fun list(): List<HealthResource>
}
