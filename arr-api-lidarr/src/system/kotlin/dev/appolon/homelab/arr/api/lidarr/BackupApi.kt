package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.BackupResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Lidarr endpoints tagged Backup. Restore takes effect server side; neither
 * restore operation documents a response schema or request body.
 */
@JvmSuppressWildcards
interface BackupApi {
    @GET("api/v1/system/backup")
    suspend fun getBackups(): List<BackupResource>

    @DELETE("api/v1/system/backup/{id}")
    suspend fun deleteBackup(@Path("id") id: Int)

    @POST("api/v1/system/backup/restore/{id}")
    suspend fun restoreBackup(@Path("id") id: Int)

    /** The spec declares no request body for the uploaded restore. */
    @POST("api/v1/system/backup/restore/upload")
    suspend fun restoreUploadedBackup()
}
