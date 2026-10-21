package dev.appolon.homelab.arr.api.radarr.api

import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Radarr `MovieLookup` tag: searches TMDB through the server so the API key
 * and proxy settings stay server-side. Results are MovieResources without a
 * local id.
 */
interface MovieLookupApi {

    /** Free-text title search; an absent term returns the empty result set. */
    @GET("api/v3/movie/lookup")
    suspend fun lookup(@Query("term") term: String? = null): List<MovieResource>

    @GET("api/v3/movie/lookup/tmdb")
    suspend fun lookupByTmdbId(@Query("tmdbId") tmdbId: Int? = null): MovieResource

    @GET("api/v3/movie/lookup/imdb")
    suspend fun lookupByImdbId(@Query("imdbId") imdbId: String? = null): MovieResource
}
