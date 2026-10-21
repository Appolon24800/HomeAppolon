package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.GET

/** Returns the quality profile template the server prefills new profiles with. */
@JvmSuppressWildcards
interface QualityProfileSchemaApi {
    @GET("api/v1/qualityprofile/schema")
    suspend fun getQualityProfileSchema(): QualityProfileResource
}
