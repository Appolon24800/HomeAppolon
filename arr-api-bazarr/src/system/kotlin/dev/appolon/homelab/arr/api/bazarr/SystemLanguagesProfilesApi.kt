package dev.appolon.homelab.arr.api.bazarr

import kotlin.jvm.JvmSuppressWildcards
import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET

/** Language profiles have no schema in the bundled spec, so the payload stays raw JSON. */
@JvmSuppressWildcards
interface SystemLanguagesProfilesApi {
    @GET("system/languages/profiles")
    suspend fun getLanguagesProfiles(): JsonObject
}
