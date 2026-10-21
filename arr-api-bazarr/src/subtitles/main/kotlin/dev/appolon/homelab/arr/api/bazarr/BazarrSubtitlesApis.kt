package dev.appolon.homelab.arr.api.bazarr

import retrofit2.Retrofit

/** Uses a server-root base URL, including any deployment prefix, but excluding api/. */
object BazarrSubtitlesApis {
    fun subtitles(retrofit: Retrofit): SubtitlesApi = retrofit.create(SubtitlesApi::class.java)
    fun subtitleContents(retrofit: Retrofit): SubtitleContentsApi =
        retrofit.create(SubtitleContentsApi::class.java)
    fun subtitlesInfo(retrofit: Retrofit): SubtitlesInfoApi =
        retrofit.create(SubtitlesInfoApi::class.java)
    fun providers(retrofit: Retrofit): ProvidersApi = retrofit.create(ProvidersApi::class.java)
    fun providersEpisodes(retrofit: Retrofit): ProvidersEpisodesApi =
        retrofit.create(ProvidersEpisodesApi::class.java)
    fun providersMovies(retrofit: Retrofit): ProvidersMoviesApi =
        retrofit.create(ProvidersMoviesApi::class.java)
    fun historyStatistics(retrofit: Retrofit): HistoryStatisticsApi =
        retrofit.create(HistoryStatisticsApi::class.java)
    fun systemSearches(retrofit: Retrofit): SystemSearchesApi =
        retrofit.create(SystemSearchesApi::class.java)
    fun webhooksPlex(retrofit: Retrofit): WebhooksPlexApi =
        retrofit.create(WebhooksPlexApi::class.java)
    fun webhooksRadarr(retrofit: Retrofit): WebhooksRadarrApi =
        retrofit.create(WebhooksRadarrApi::class.java)
    fun webhooksSonarr(retrofit: Retrofit): WebhooksSonarrApi =
        retrofit.create(WebhooksSonarrApi::class.java)
    fun plexAuthentication(retrofit: Retrofit): PlexAuthenticationApi =
        retrofit.create(PlexAuthenticationApi::class.java)
    fun jellyfin(retrofit: Retrofit): JellyfinApi = retrofit.create(JellyfinApi::class.java)
    fun filesBrowserforBazarr(retrofit: Retrofit): FilesBrowserforBazarrApi =
        retrofit.create(FilesBrowserforBazarrApi::class.java)
    fun filesBrowserforRadarr(retrofit: Retrofit): FilesBrowserforRadarrApi =
        retrofit.create(FilesBrowserforRadarrApi::class.java)
    fun filesBrowserforSonarr(retrofit: Retrofit): FilesBrowserforSonarrApi =
        retrofit.create(FilesBrowserforSonarrApi::class.java)
}
