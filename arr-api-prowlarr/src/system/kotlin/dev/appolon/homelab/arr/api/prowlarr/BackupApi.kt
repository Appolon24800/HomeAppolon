package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.BackupResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Backup tag: list, delete and restore server backup zips. The spec declares
 * no request or response content for the restore calls, so they return
 * [Response]; acceptance is the whole result.
 */
@JvmSuppressWildcards
interface BackupApi {
    @GET("api/v1/system/backup")
    suspend fun getBackups(): List<BackupResource>

    @DELETE("api/v1/system/backup/{id}")
    suspend fun deleteBackup(@Path("id") id: Int): Response<Unit>

    @POST("api/v1/system/backup/restore/{id}")
    suspend fun restoreBackup(@Path("id") id: Int): Response<Unit>

    /**
     * Restore from an uploaded archive. The spec declares no request body
     * for this route, so nothing is sent; wiring the actual multipart file
     * part needs a spec the server documents.
     */
    @POST("api/v1/system/backup/restore/upload")
    suspend fun restoreBackupUpload(): Response<Unit>
}
