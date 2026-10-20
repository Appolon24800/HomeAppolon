package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.ApiInfoResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.GET

/** Operations in the Sonarr ApiInfo OpenAPI tag. */
@JvmSuppressWildcards
interface ApiInfoApi {
    @GET("api")
    suspend fun get(): ApiInfoResource
}
