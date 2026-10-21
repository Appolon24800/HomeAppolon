package dev.appolon.homelab.arr.api.bazarr

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

/** String flags follow the spec, and 204, 404, 409 and 500 remain distinguishable. */
interface EpisodesSubtitlesApi {
    @PATCH("api/episodes/subtitles")
    suspend fun patchEpisodesSubtitles(
        @Query("seriesid") seriesId: Int,
        @Query("episodeid") episodeId: Int,
        @Query("language") language: String,
        @Query("forced") forced: String,
        @Query("hi") hi: String,
    ): Response<Unit>

    @Multipart
    @POST("api/episodes/subtitles")
    suspend fun postEpisodesSubtitles(
        @Query("seriesid") seriesId: Int,
        @Query("episodeid") episodeId: Int,
        @Query("language") language: String,
        @Query("forced") forced: String,
        @Query("hi") hi: String,
        @Part file: MultipartBody.Part,
    ): Response<Unit>

    @DELETE("api/episodes/subtitles")
    suspend fun deleteEpisodesSubtitles(
        @Query("seriesid") seriesId: Int,
        @Query("episodeid") episodeId: Int,
        @Query("language") language: String,
        @Query("forced") forced: String,
        @Query("hi") hi: String,
        @Query("path") path: String,
    ): Response<Unit>
}

/** String flags follow the spec, and 204, 404, 409 and 500 remain distinguishable. */
interface MoviesSubtitlesApi {
    @PATCH("api/movies/subtitles")
    suspend fun patchMoviesSubtitles(
        @Query("radarrid") radarrId: Int,
        @Query("language") language: String,
        @Query("forced") forced: String,
        @Query("hi") hi: String,
    ): Response<Unit>

    @Multipart
    @POST("api/movies/subtitles")
    suspend fun postMoviesSubtitles(
        @Query("radarrid") radarrId: Int,
        @Query("language") language: String,
        @Query("forced") forced: String,
        @Query("hi") hi: String,
        @Part file: MultipartBody.Part,
    ): Response<Unit>

    @DELETE("api/movies/subtitles")
    suspend fun deleteMoviesSubtitles(
        @Query("radarrid") radarrId: Int,
        @Query("language") language: String,
        @Query("forced") forced: String,
        @Query("hi") hi: String,
        @Query("path") path: String,
    ): Response<Unit>
}
