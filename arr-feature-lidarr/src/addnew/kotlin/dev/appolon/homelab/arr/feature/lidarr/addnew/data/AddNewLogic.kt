package dev.appolon.homelab.arr.feature.lidarr.addnew.data

import dev.appolon.homelab.arr.api.lidarr.model.AddAlbumOptions
import dev.appolon.homelab.arr.api.lidarr.model.AddArtistOptions
import dev.appolon.homelab.arr.api.lidarr.model.AlbumAddType
import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.AlbumStudioArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.AlbumStudioResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.MediaCoverTypes
import dev.appolon.homelab.arr.api.lidarr.model.MonitorTypes
import dev.appolon.homelab.arr.api.lidarr.model.MonitoringOptions
import dev.appolon.homelab.arr.api.lidarr.model.NewItemMonitorTypes
import dev.appolon.homelab.arr.core.ArrImages
import dev.appolon.homelab.arr.core.CoverType

/**
 * Pure presentation and payload logic for the Add new screens, kept free of
 * Android and IO so every rule is unit testable.
 */
object AddNewLogic {

    /** Monitoring strategies the add sheet offers, in web-client order. */
    val MONITOR_STRATEGIES = listOf(
        MonitorTypes.ALL,
        MonitorTypes.FUTURE,
        MonitorTypes.MISSING,
        MonitorTypes.LATEST,
        MonitorTypes.NONE,
    )

    /** Strategies the AlbumStudio picker offers as bulk preselections. */
    val STUDIO_STRATEGIES = listOf(
        MonitorTypes.ALL,
        MonitorTypes.FUTURE,
        MonitorTypes.MISSING,
        MonitorTypes.LATEST,
        MonitorTypes.NONE,
    )

    /**
     * Cover URL for one artist. Library artists (id != 0) with a poster
     * image use the local MediaCover path, which rides the authenticated
     * image loader; lookup candidates only carry remote art. Null means the
     * caller shows the monogram tile. MediaCover endpoints never go through
     * Retrofit.
     */
    fun artistCoverUrl(artist: ArtistResource, baseUrl: String): String? {
        val poster = artist.images.orEmpty().firstOrNull { it.coverType == MediaCoverTypes.POSTER }
        if (artist.id != 0 && poster != null) {
            // ArrImages paths carry their own leading slash, so the trimmed
            // base concatenates directly.
            return baseUrl.trimEnd('/') +
                ArrImages.lidarrArtistCover(artist.id.toLong(), CoverType.POSTER)
        }
        return remoteCoverUrl(poster?.remoteUrl ?: artist.remotePoster)
    }

    /** Cover URL for one album; same local-then-remote rule as artists. */
    fun albumCoverUrl(album: AlbumResource, baseUrl: String): String? {
        val cover = album.images.orEmpty().firstOrNull { it.coverType == MediaCoverTypes.COVER }
        if (album.id != 0 && cover != null) {
            return baseUrl.trimEnd('/') +
                ArrImages.lidarrAlbumCover(album.id.toLong(), CoverType.COVER)
        }
        return remoteCoverUrl(cover?.remoteUrl ?: album.remoteCover)
    }

    /** Lidarr's remote art lives on absolute http(s) URLs; anything else is unfetchable. */
    private fun remoteCoverUrl(url: String?): String? = url?.takeIf {
        it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
    }

    /** MusicBrainz ids of the library's artists, the existing-in-library key. */
    fun existingForeignArtistIds(artists: List<ArtistResource>): Set<String> = artists
        .mapNotNull { it.foreignArtistId }
        .toSet()

    /** MusicBrainz ids of the library's albums, the existing-in-library key. */
    fun existingForeignAlbumIds(albums: List<AlbumResource>): Set<String> = albums
        .mapNotNull { it.foreignAlbumId }
        .toSet()

    /** True when the album has no track files, the "missing" state of the studio picker. */
    fun isMissing(album: AlbumResource): Boolean =
        (album.statistics?.trackFileCount ?: 0) == 0

    /**
     * Bulk preselection for the studio picker. FUTURE picks unreleased
     * albums (releaseDate parses as a date in the future or stays unparsed,
     * which upcoming announcements are), LATEST picks the newest album,
     * NONE keeps the current monitored state, and the rest follow their
     * name. Dates stay unparsed strings everywhere else; here a LocalDate
     * parse decides "future".
     */
    fun preselect(albums: List<AlbumResource>, strategy: MonitorTypes): Map<Int, Boolean> {
        val byId = albums.associateBy { it.id }
        return when (strategy) {
            MonitorTypes.ALL -> byId.mapValues { true }
            MonitorTypes.NONE -> byId.mapValues { it.value.monitored }
            MonitorTypes.MISSING -> byId.mapValues { isMissing(it.value) }
            MonitorTypes.FUTURE -> byId.mapValues { isFuture(it.value) }
            MonitorTypes.LATEST -> {
                val newest = albums
                    .filter { it.releaseDate != null }
                    .maxByOrNull { it.releaseDate.orEmpty() }
                byId.mapValues { it.value.id == newest?.id }
            }

            else -> byId.mapValues { it.value.monitored }
        }
    }

    private fun isFuture(album: AlbumResource): Boolean {
        val date = runCatching {
            java.time.LocalDate.parse(album.releaseDate.orEmpty())
        }.getOrNull() ?: return false
        return date.isAfter(java.time.LocalDate.now())
    }

    /**
     * Builds the POST /api/v1/artist body for one add, mirroring the web
     * client: the lookup result supplies the metadata, the form supplies the
     * settings, and `addOptions.monitor` plus `monitored` encode the chosen
     * monitoring strategy. `monitorNewItems` derives from the same strategy
     * so future releases follow it.
     */
    fun toAddArtistPayload(
        candidate: ArtistResource,
        rootFolderPath: String,
        qualityProfileId: Int,
        metadataProfileId: Int,
        monitor: MonitorTypes,
        tags: Set<Int>,
        searchForMissingAlbums: Boolean,
    ): ArtistResource = candidate.copy(
        id = 0,
        monitored = monitor != MonitorTypes.NONE,
        monitorNewItems = when (monitor) {
            MonitorTypes.NONE -> NewItemMonitorTypes.NONE
            MonitorTypes.FUTURE -> NewItemMonitorTypes.NEW
            else -> NewItemMonitorTypes.ALL
        },
        qualityProfileId = qualityProfileId,
        metadataProfileId = metadataProfileId,
        rootFolderPath = rootFolderPath,
        path = null,
        folder = null,
        tags = tags.toList(),
        statistics = null,
        addOptions = AddArtistOptions(
            monitor = monitor,
            monitored = monitor != MonitorTypes.NONE,
            searchForMissingAlbums = searchForMissingAlbums,
        ),
    )

    /**
     * Builds the POST /api/v1/album body for one standalone add. Lidarr
     * files standalone albums under an existing library artist, so the
     * caller resolves [artistId] first; the manual add type keeps the
     * server from second-guessing the explicit album choice.
     */
    fun toAddAlbumPayload(
        candidate: AlbumResource,
        artistId: Int,
        monitored: Boolean,
        searchForNewAlbum: Boolean,
    ): AlbumResource = candidate.copy(
        id = 0,
        artistId = artistId,
        monitored = monitored,
        statistics = null,
        addOptions = AddAlbumOptions(
            addType = AlbumAddType.MANUAL,
            searchForNewAlbum = searchForNewAlbum,
        ),
    )

    /**
     * Builds the POST /api/v1/albumstudio body carrying the exact target
     * monitored state of every album: explicit per-album flags win over the
     * bulk strategy on the server, so the selection is authoritative while
     * `monitoringOptions` still records the strategy the user picked.
     */
    fun toAlbumStudioPayload(
        artistId: Int,
        albums: List<AlbumResource>,
        targets: Map<Int, Boolean>,
        strategy: MonitorTypes,
    ): AlbumStudioResource = AlbumStudioResource(
        artist = listOf(
            AlbumStudioArtistResource(
                id = artistId,
                albums = albums.map { album ->
                    album.copy(monitored = targets[album.id] ?: album.monitored)
                },
            ),
        ),
        monitoringOptions = MonitoringOptions(
            monitor = strategy,
            monitored = strategy != MonitorTypes.NONE,
        ),
        monitorNewItems = NewItemMonitorTypes.NONE,
    )

    /** Applies a studio payload to cached album rows; used after both sync and queue paths. */
    fun applyStudioToAlbums(
        albums: List<AlbumResource>,
        payload: AlbumStudioResource,
    ): List<AlbumResource> {
        val entry = payload.artist.orEmpty().firstOrNull() ?: return albums
        val targets = entry.albums.orEmpty().associate { it.id to it.monitored }
        return albums.map { album ->
            targets[album.id]?.let { album.copy(monitored = it) } ?: album
        }
    }
}
