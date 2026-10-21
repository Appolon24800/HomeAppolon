package dev.appolon.homelab.arr.api.bazarr

import kotlin.jvm.JvmSuppressWildcards
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** Announcement payloads have no schema in the bundled spec, so reads stay raw JSON. */
@JvmSuppressWildcards
interface SystemAnnouncementsApi {
    @GET("api/system/announcements")
    suspend fun getSystemAnnouncements(): JsonObject

    @POST("api/system/announcements")
    suspend fun postSystemAnnouncements(@Query("hash") hash: String): Response<Unit>
}
