package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.ReleaseResource
import retrofit2.http.Body
import retrofit2.http.POST

/** Operations in the Sonarr ReleasePush OpenAPI tag. */
@JvmSuppressWildcards
interface ReleasePushApi {
    @POST("api/v3/release/push")
    suspend fun create(
        @Body body: ReleaseResource
    ): List<ReleaseResource>
}
