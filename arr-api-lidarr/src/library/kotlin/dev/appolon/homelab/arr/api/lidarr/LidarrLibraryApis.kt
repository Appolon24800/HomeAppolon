package dev.appolon.homelab.arr.api.lidarr

import retrofit2.Retrofit

/** Creates the library tag clients using the connection layer's Retrofit. */
object LidarrLibraryApis {
    fun album(retrofit: Retrofit): AlbumApi =
        retrofit.create(AlbumApi::class.java)

    fun albumLookup(retrofit: Retrofit): AlbumLookupApi =
        retrofit.create(AlbumLookupApi::class.java)

    fun albumStudio(retrofit: Retrofit): AlbumStudioApi =
        retrofit.create(AlbumStudioApi::class.java)

    fun artist(retrofit: Retrofit): ArtistApi =
        retrofit.create(ArtistApi::class.java)

    fun artistEditor(retrofit: Retrofit): ArtistEditorApi =
        retrofit.create(ArtistEditorApi::class.java)

    fun artistLookup(retrofit: Retrofit): ArtistLookupApi =
        retrofit.create(ArtistLookupApi::class.java)

    fun mediaCover(retrofit: Retrofit): MediaCoverApi =
        retrofit.create(MediaCoverApi::class.java)

    fun renameTrack(retrofit: Retrofit): RenameTrackApi =
        retrofit.create(RenameTrackApi::class.java)

    fun retagTrack(retrofit: Retrofit): RetagTrackApi =
        retrofit.create(RetagTrackApi::class.java)

    fun track(retrofit: Retrofit): TrackApi =
        retrofit.create(TrackApi::class.java)

    fun trackFile(retrofit: Retrofit): TrackFileApi =
        retrofit.create(TrackFileApi::class.java)

}
