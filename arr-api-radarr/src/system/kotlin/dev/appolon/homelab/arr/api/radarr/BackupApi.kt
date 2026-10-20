package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.BackupResource
import dev.appolon.homelab.arr.api.radarr.model.BackupRestoreResource
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Radarr endpoints tagged Backup. */
@JvmSuppressWildcards
interface BackupApi {
    @GET("api/v3/system/backup")
    suspend fun backups(): List<BackupResource>

    @DELETE("api/v3/system/backup/{id}")
    suspend fun removeBackup(
        @Path("id") id: Int,
    ): Response<Unit>

    /** 200 carries a [BackupRestoreResource], 400 a BackupRestoreErrorResource. */
    @POST("api/v3/system/backup/restore/{id}")
    suspend fun restoreBackup(
        @Path("id") id: Int,
    ): Response<BackupRestoreResource>

    /** 200 carries a [BackupRestoreResource], 400 a BackupRestoreErrorResource. */
    @POST("api/v3/system/backup/restore/upload")
    suspend fun restoreBackupUpload(): Response<BackupRestoreResource>
}
