package dev.appolon.homelab.arr.api.sonarr

import retrofit2.Retrofit

object SonarrLibraryApis {
    fun series(retrofit: Retrofit): SeriesApi = retrofit.create(SeriesApi::class.java)
    fun seriesEditor(retrofit: Retrofit): SeriesEditorApi =
        retrofit.create(SeriesEditorApi::class.java)
    fun seriesFolder(retrofit: Retrofit): SeriesFolderApi =
        retrofit.create(SeriesFolderApi::class.java)
    fun seriesImport(retrofit: Retrofit): SeriesImportApi =
        retrofit.create(SeriesImportApi::class.java)
    fun seasonPass(retrofit: Retrofit): SeasonPassApi = retrofit.create(SeasonPassApi::class.java)
    fun episode(retrofit: Retrofit): EpisodeApi = retrofit.create(EpisodeApi::class.java)
    fun episodeFile(retrofit: Retrofit): EpisodeFileApi =
        retrofit.create(EpisodeFileApi::class.java)
    fun renameEpisode(retrofit: Retrofit): RenameEpisodeApi =
        retrofit.create(RenameEpisodeApi::class.java)
    fun mediaCover(retrofit: Retrofit): MediaCoverApi = retrofit.create(MediaCoverApi::class.java)
}
