package dev.appolon.homelab.arr.api.bazarr

import kotlin.jvm.JvmSuppressWildcards
import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Query

/** Language lists have no schema in the bundled spec, so the payload stays raw JSON. */
@JvmSuppressWildcards
interface SystemLanguagesApi {
    @GET("api/system/languages")
    suspend fun getLanguages(@Query("history") history: String? = null): JsonObject
}
