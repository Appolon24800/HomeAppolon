package dev.appolon.homelab.arr.api.prowlarr

import kotlin.jvm.JvmSuppressWildcards
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * FileSystem tag: server-side directory browsing for path pickers. The spec
 * declares no response schema for either route. The listing endpoint answers
 * with a directories/files object, which passes through as [JsonObject];
 * the type endpoint answers with a bare JSON string, so it passes through as
 * the wider [JsonElement] because JsonObject would reject a primitive.
 */
@JvmSuppressWildcards
interface FileSystemApi {
    @GET("api/v1/filesystem")
    suspend fun getFileSystem(
        @Query("path") path: String? = null,
        @Query("includeFiles") includeFiles: Boolean? = null,
        @Query("allowFoldersWithoutTrailingSlashes") allowFoldersWithoutTrailingSlashes:
            Boolean? = null,
    ): JsonObject

    @GET("api/v1/filesystem/type")
    suspend fun getFileSystemType(@Query("path") path: String? = null): JsonElement
}
