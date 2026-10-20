package dev.appolon.homelab.arr.api.bazarr

import kotlin.jvm.JvmSuppressWildcards
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

/** Backup listings have no schema in the bundled spec, so reads stay raw JSON. */
@JvmSuppressWildcards
interface SystemBackupsApi {
    @GET("system/backups")
    suspend fun getSystemBackups(): JsonObject

    @POST("system/backups")
    suspend fun postSystemBackups(): Response<Unit>

    @PATCH("system/backups")
    suspend fun patchSystemBackups(@Query("filename") filename: String): Response<Unit>

    @DELETE("system/backups")
    suspend fun deleteSystemBackups(@Query("filename") filename: String): Response<Unit>
}
