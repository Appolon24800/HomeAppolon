package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.DiskSpaceResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.GET

/** Lidarr endpoints tagged DiskSpace. */
@JvmSuppressWildcards
interface DiskSpaceApi {
    @GET("api/v1/diskspace")
    suspend fun getDiskSpace(): List<DiskSpaceResource>
}
