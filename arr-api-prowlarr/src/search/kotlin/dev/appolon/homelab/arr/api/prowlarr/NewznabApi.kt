package dev.appolon.homelab.arr.api.prowlarr

import kotlin.jvm.JvmSuppressWildcards
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Newznab tag: proxy queries and downloads through a single indexer. The spec
 * declares no response content for any of these operations because the wire
 * body is newznab XML for the API calls and arbitrary bytes for downloads, so
 * every method returns OkHttp's streaming [ResponseBody] instead of a JSON
 * DTO. Callers must consume the body and close it.
 *
 * Each operation has a modern path under `api/v1/indexer/{id}` and a legacy
 * path that Prowlarr exposes so external apps can treat the server itself as
 * one newznab indexer.
 */
@JvmSuppressWildcards
interface NewznabApi {
    @GET("api/v1/indexer/{id}/newznab")
    suspend fun getNewznab(
        @Path("id") id: Int,
        @Query("t") t: String?,
        @Query("q") q: String?,
        @Query("cat") cat: String?,
        @Query("imdbid") imdbid: String?,
        @Query("tmdbid") tmdbid: Int?,
        @Query("extended") extended: String?,
        @Query("limit") limit: Int?,
        @Query("offset") offset: Int?,
        @Query("minage") minage: Int?,
        @Query("maxage") maxage: Int?,
        @Query("minsize") minsize: Long?,
        @Query("maxsize") maxsize: Long?,
        @Query("rid") rid: Int?,
        @Query("tvmazeid") tvmazeid: Int?,
        @Query("traktid") traktid: Int?,
        @Query("tvdbid") tvdbid: Int?,
        @Query("doubanid") doubanid: Int?,
        @Query("season") season: Int?,
        @Query("ep") ep: String?,
        @Query("album") album: String?,
        @Query("artist") artist: String?,
        @Query("label") label: String?,
        @Query("track") track: String?,
        @Query("year") year: Int?,
        @Query("genre") genre: String?,
        @Query("author") author: String?,
        @Query("title") title: String?,
        @Query("publisher") publisher: String?,
        @Query("configured") configured: String?,
        @Query("source") source: String?,
        @Query("host") host: String?,
        @Query("server") server: String?,
    ): ResponseBody

    @GET("{id}/api")
    suspend fun getNewznabLegacy(
        @Path("id") id: Int,
        @Query("t") t: String?,
        @Query("q") q: String?,
        @Query("cat") cat: String?,
        @Query("imdbid") imdbid: String?,
        @Query("tmdbid") tmdbid: Int?,
        @Query("extended") extended: String?,
        @Query("limit") limit: Int?,
        @Query("offset") offset: Int?,
        @Query("minage") minage: Int?,
        @Query("maxage") maxage: Int?,
        @Query("minsize") minsize: Long?,
        @Query("maxsize") maxsize: Long?,
        @Query("rid") rid: Int?,
        @Query("tvmazeid") tvmazeid: Int?,
        @Query("traktid") traktid: Int?,
        @Query("tvdbid") tvdbid: Int?,
        @Query("doubanid") doubanid: Int?,
        @Query("season") season: Int?,
        @Query("ep") ep: String?,
        @Query("album") album: String?,
        @Query("artist") artist: String?,
        @Query("label") label: String?,
        @Query("track") track: String?,
        @Query("year") year: Int?,
        @Query("genre") genre: String?,
        @Query("author") author: String?,
        @Query("title") title: String?,
        @Query("publisher") publisher: String?,
        @Query("configured") configured: String?,
        @Query("source") source: String?,
        @Query("host") host: String?,
        @Query("server") server: String?,
    ): ResponseBody

    @GET("api/v1/indexer/{id}/download")
    suspend fun getDownload(
        @Path("id") id: Int,
        @Query("link") link: String?,
        @Query("file") file: String?,
    ): ResponseBody

    @GET("{id}/download")
    suspend fun getDownloadLegacy(
        @Path("id") id: Int,
        @Query("link") link: String?,
        @Query("file") file: String?,
    ): ResponseBody
}
