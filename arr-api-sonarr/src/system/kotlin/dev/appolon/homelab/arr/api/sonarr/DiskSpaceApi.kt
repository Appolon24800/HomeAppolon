package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.DiskSpaceResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.GET

/** Operations in the Sonarr DiskSpace OpenAPI tag. */
@JvmSuppressWildcards
interface DiskSpaceApi {
    @GET("api/v3/diskspace")
    suspend fun list(): List<DiskSpaceResource>
}
