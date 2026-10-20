package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.LocalizationOption
import kotlin.jvm.JvmSuppressWildcards
import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET

/**
 * Localization tag: UI language support. The dictionary route declares no
 * response schema; the server answers with a string table, which passes
 * through as [JsonObject] for screens to look up keys.
 */
@JvmSuppressWildcards
interface LocalizationApi {
    @GET("api/v1/localization")
    suspend fun getLocalization(): JsonObject

    @GET("api/v1/localization/options")
    suspend fun getLocalizationOptions(): List<LocalizationOption>
}
