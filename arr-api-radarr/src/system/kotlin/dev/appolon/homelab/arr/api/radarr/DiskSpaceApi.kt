package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.DiskSpaceResource
import retrofit2.http.GET

/** Radarr endpoints tagged DiskSpace. */
@JvmSuppressWildcards
interface DiskSpaceApi {
    @GET("api/v3/diskspace")
    suspend fun diskSpace(): List<DiskSpaceResource>
}
