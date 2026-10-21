package dev.appolon.homelab.arr.api.sonarr

import kotlin.jvm.JvmSuppressWildcards
import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Operations in the Sonarr FileSystem OpenAPI tag. The spec declares no
 * response schema for any of these endpoints, so listings, media type checks
 * and media file listings pass through as raw JSON objects.
 */
@JvmSuppressWildcards
interface FileSystemApi {
    @GET("api/v3/filesystem")
    suspend fun list(
        @Query("path") path: String? = null,
        @Query("includeFiles") includeFiles: Boolean? = null,
        @Query("allowFoldersWithoutTrailingSlashes") allowFoldersWithoutTrailingSlashes: Boolean? = null
    ): JsonObject

    @GET("api/v3/filesystem/type")
    suspend fun type(@Query("path") path: String? = null): JsonObject

    @GET("api/v3/filesystem/mediafiles")
    suspend fun mediaFiles(@Query("path") path: String? = null): JsonObject
}
