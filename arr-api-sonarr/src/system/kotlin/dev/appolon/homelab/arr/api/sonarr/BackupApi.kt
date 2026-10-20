package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.BackupResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Operations in the Sonarr Backup OpenAPI tag. */
@JvmSuppressWildcards
interface BackupApi {
    @GET("api/v3/system/backup")
    suspend fun list(): List<BackupResource>

    @DELETE("api/v3/system/backup/{id}")
    suspend fun delete(@Path("id") id: Int): Unit

    @POST("api/v3/system/backup/restore/{id}")
    suspend fun restore(@Path("id") id: Int): Unit

    @POST("api/v3/system/backup/restore/upload")
    suspend fun restoreUpload(): Unit
}
