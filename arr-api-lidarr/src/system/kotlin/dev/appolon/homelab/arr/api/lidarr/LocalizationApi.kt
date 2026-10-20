package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.LocalizationResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.GET

/** Lidarr endpoints tagged Localization. */
@JvmSuppressWildcards
interface LocalizationApi {
    @GET("api/v1/localization")
    suspend fun getLocalization(): LocalizationResource
}
