package dev.appolon.homelab.arr.api.bazarr

import kotlin.jvm.JvmSuppressWildcards
import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET

/** Release listings have no schema in the bundled spec, so the payload stays raw JSON. */
@JvmSuppressWildcards
interface SystemReleasesApi {
    @GET("system/releases")
    suspend fun getSystemReleases(): JsonObject
}
