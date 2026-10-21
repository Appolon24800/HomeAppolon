package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.ImportListMoviesResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** Radarr endpoints tagged ImportListMovies. */
interface ImportListMoviesApi {
    @GET("api/v3/importlist/movie")
    suspend fun importListMovies(
        @Query("includeRecommendations") includeRecommendations: Boolean? = null,
        @Query("includeTrending") includeTrending: Boolean? = null,
        @Query("includePopular") includePopular: Boolean? = null,
    ): List<ImportListMoviesResource>

    @POST("api/v3/importlist/movie")
    suspend fun addMovies(
        @Body body: List<MovieResource>,
    ): List<MovieResource>

}
