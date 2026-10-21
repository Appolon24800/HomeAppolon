package dev.appolon.homelab.arr.api.bazarr

import retrofit2.Retrofit

/** Uses the connection layer's configured base URL, authentication and JSON converter. */
object BazarrLibraryApis {
    fun series(retrofit: Retrofit): SeriesApi = retrofit.create(SeriesApi::class.java)
    fun seriesTags(retrofit: Retrofit): SeriesTagsApi = retrofit.create(SeriesTagsApi::class.java)
    fun episodes(retrofit: Retrofit): EpisodesApi = retrofit.create(EpisodesApi::class.java)
    fun episodesSubtitles(retrofit: Retrofit): EpisodesSubtitlesApi =
        retrofit.create(EpisodesSubtitlesApi::class.java)
    fun episodesBlacklist(retrofit: Retrofit): EpisodesBlacklistApi =
        retrofit.create(EpisodesBlacklistApi::class.java)
    fun episodesHistory(retrofit: Retrofit): EpisodesHistoryApi =
        retrofit.create(EpisodesHistoryApi::class.java)
    fun episodesWanted(retrofit: Retrofit): EpisodesWantedApi =
        retrofit.create(EpisodesWantedApi::class.java)
    fun movies(retrofit: Retrofit): MoviesApi = retrofit.create(MoviesApi::class.java)
    fun moviesTags(retrofit: Retrofit): MoviesTagsApi = retrofit.create(MoviesTagsApi::class.java)
    fun moviesSubtitles(retrofit: Retrofit): MoviesSubtitlesApi =
        retrofit.create(MoviesSubtitlesApi::class.java)
    fun moviesBlacklist(retrofit: Retrofit): MoviesBlacklistApi =
        retrofit.create(MoviesBlacklistApi::class.java)
    fun moviesHistory(retrofit: Retrofit): MoviesHistoryApi =
        retrofit.create(MoviesHistoryApi::class.java)
    fun moviesWanted(retrofit: Retrofit): MoviesWantedApi =
        retrofit.create(MoviesWantedApi::class.java)
    fun badges(retrofit: Retrofit): BadgesApi = retrofit.create(BadgesApi::class.java)
}
