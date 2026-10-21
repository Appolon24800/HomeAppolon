package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.QualityProfileResource
import retrofit2.http.GET
import kotlin.jvm.JvmSuppressWildcards

/**
 * Radarr endpoints tagged QualityProfileSchema. The blank profile the editor
 * form is built from; the server fills `items` with every known quality.
 */
@JvmSuppressWildcards
interface QualityProfileSchemaApi {
    @GET("api/v3/qualityprofile/schema")
    suspend fun qualityProfileSchema(): QualityProfileResource
}
