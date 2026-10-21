package dev.appolon.homelab.arr.api.lidarr

import kotlin.jvm.JvmSuppressWildcards
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Lidarr endpoints tagged FileSystem: the folder browser backing manual import
 * and root folder picking. The spec documents no response schema for any of
 * the three operations, so each passes through as raw JSON. The listing is an
 * object; the type probe answers with a JSON string and mediafiles with an
 * array, so those two use JsonElement rather than JsonObject.
 */
@JvmSuppressWildcards
interface FileSystemApi {
    @GET("api/v1/filesystem")
    suspend fun getFileSystem(
        @Query("path") path: String? = null,
        @Query("includeFiles") includeFiles: Boolean? = null,
        @Query("allowFoldersWithoutTrailingSlashes") allowFoldersWithoutTrailingSlashes: Boolean? =
            null,
    ): JsonObject

    @GET("api/v1/filesystem/type")
    suspend fun getFileSystemType(@Query("path") path: String? = null): JsonElement

    @GET("api/v1/filesystem/mediafiles")
    suspend fun getMediaFiles(@Query("path") path: String? = null): JsonElement
}
