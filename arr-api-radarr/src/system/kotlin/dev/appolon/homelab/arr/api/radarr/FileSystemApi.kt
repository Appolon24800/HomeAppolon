package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.FileSystemMediaFileResource
import dev.appolon.homelab.arr.api.radarr.model.FileSystemResult
import dev.appolon.homelab.arr.api.radarr.model.FileSystemTypeResource
import retrofit2.http.GET
import retrofit2.http.Query

/** Radarr endpoints tagged FileSystem. */
@JvmSuppressWildcards
interface FileSystemApi {
    @GET("api/v3/filesystem")
    suspend fun fileSystem(
        @Query("path") path: String? = null,
        @Query("includeFiles") includeFiles: Boolean? = null,
        @Query("allowFoldersWithoutTrailingSlashes") allowFoldersWithoutTrailingSlashes: Boolean? = null,
    ): FileSystemResult

    @GET("api/v3/filesystem/type")
    suspend fun fileSystemType(
        @Query("path") path: String? = null,
    ): FileSystemTypeResource

    @GET("api/v3/filesystem/mediafiles")
    suspend fun fileSystemMediaFiles(
        @Query("path") path: String? = null,
    ): List<FileSystemMediaFileResource>
}
