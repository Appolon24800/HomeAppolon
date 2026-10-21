package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.UpdateResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.GET

/** Operations in the Sonarr Update OpenAPI tag. */
@JvmSuppressWildcards
interface UpdateApi {
    @GET("api/v3/update")
    suspend fun list(): List<UpdateResource>
}
