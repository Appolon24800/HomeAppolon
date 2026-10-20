package dev.appolon.homelab.arr.feature.lidarr.library.data

import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.core.ArrTime
import dev.appolon.homelab.arr.core.FilterDefinition
import dev.appolon.homelab.arr.core.FilterSpec
import dev.appolon.homelab.arr.core.FilterState
import dev.appolon.homelab.arr.core.FilterValue
import dev.appolon.homelab.arr.core.SortSpec
import dev.appolon.homelab.arr.core.applyFilters
import dev.appolon.homelab.arr.core.sortItems

/**
 * Filter, sort, and paging presentation for the artists library, built on the
 * `:arr-core` Filters framework. Everything here is pure so the ViewModel is a
 * thin shell and behavior is unit tested directly.
 *
 * Filter ids: `monitored` (Toggle), `quality` (Choice of profile ids),
 * `metadata` (Choice of profile ids), `genre` (Choice), `albums` (Range over
 * the per-artist album count), `folder` (Choice), `tags` (Choice of tag ids),
 * `missing` (Toggle over the album summary's missing count). Choice options
 * are strings so profile and tag ids ride the same [FilterValue.Choice] shape.
 */
object ArtistLibraryFilters {

    const val MONITORED = "monitored"
    const val QUALITY = "quality"
    const val METADATA = "metadata"
    const val GENRE = "genre"
    const val ALBUMS = "albums"
    const val FOLDER = "folder"
    const val TAGS = "tags"
    const val MISSING = "missing"

    /** Option universe derived from the library itself when profiles are unavailable. */
    data class Options(
        val genres: List<String> = emptyList(),
        val folders: List<String> = emptyList(),
        val tagIds: List<Int> = emptyList(),
        val qualityProfileIds: List<Int> = emptyList(),
        val metadataProfileIds: List<Int> = emptyList(),
        val minAlbums: Double = 0.0,
        val maxAlbums: Double = 50.0,
    ) {
        val albumSpan: ClosedFloatingPointRange<Double> get() = minAlbums..maxAlbums
    }

    /** Extracts every choice option plus the album-count span from the artists in view. */
    fun optionsFrom(
        artists: List<ArtistResource>,
        summaries: Map<Int, ArtistAlbumSummary> = emptyMap(),
    ): Options {
        val albumCounts = artists.map { summaries[it.id]?.albumCount ?: it.statistics?.albumCount ?: 0 }
        val observedMax = (albumCounts.maxOrNull() ?: 0).toDouble()
        return Options(
            genres = artists.flatMap { it.genres.orEmpty() }.distinct().sorted(),
            folders = artists.mapNotNull { folderOf(it) }.distinct().sorted(),
            tagIds = artists.flatMap { it.tags.orEmpty() }.distinct().sorted(),
            qualityProfileIds = artists.map { it.qualityProfileId }.distinct().sorted(),
            metadataProfileIds = artists.map { it.metadataProfileId }.distinct().sorted(),
            minAlbums = 0.0,
            maxAlbums = observedMax.coerceAtLeast(1.0),
        )
    }

    /** The folder an artist lives in, preferring the explicit fields over the path. */
    fun folderOf(artist: ArtistResource): String? =
        artist.folder ?: artist.rootFolderPath ?: artist.path?.substringAfterLast('/')

    /** True when any monitored album of the artist has no files, from the album rollup. */
    fun isMissing(artist: ArtistResource, summaries: Map<Int, ArtistAlbumSummary>): Boolean =
        summaries[artist.id]?.hasMissing ?: false

    /** Album count shown for an artist: the rollup when albums loaded, else statistics. */
    fun albumCountOf(artist: ArtistResource, summaries: Map<Int, ArtistAlbumSummary>): Int =
        summaries[artist.id]?.albumCount ?: artist.statistics?.albumCount ?: 0

    /** Specs for rendering filter controls, in the order the filter bar shows them. */
    fun specs(options: Options): List<FilterSpec> = listOf(
        FilterSpec.Toggle(MONITORED),
        FilterSpec.Toggle(MISSING),
        FilterSpec.Choice(QUALITY, options.qualityProfileIds.map(Int::toString)),
        FilterSpec.Choice(METADATA, options.metadataProfileIds.map(Int::toString)),
        FilterSpec.Choice(GENRE, options.genres),
        FilterSpec.Range(ALBUMS, options.minAlbums, options.maxAlbums),
        FilterSpec.Choice(FOLDER, options.folders),
        FilterSpec.Choice(TAGS, options.tagIds.map(Int::toString)),
    )

    /** Matching logic for every spec in [specs], over [ArtistResource]. */
    fun definitions(
        options: Options,
        summaries: Map<Int, ArtistAlbumSummary>,
    ): List<FilterDefinition<ArtistResource>> = listOf(
        FilterDefinition(FilterSpec.Toggle(MONITORED)) { artist, value ->
            (value as FilterValue.Toggle).on?.let { artist.monitored == it } ?: true
        },
        FilterDefinition(FilterSpec.Toggle(MISSING)) { artist, value ->
            (value as FilterValue.Toggle).on?.let { isMissing(artist, summaries) == it } ?: true
        },
        FilterDefinition(
            FilterSpec.Choice(QUALITY, options.qualityProfileIds.map(Int::toString)),
        ) { artist, value ->
            val selected = (value as FilterValue.Choice).selected
            artist.qualityProfileId.toString() in selected
        },
        FilterDefinition(
            FilterSpec.Choice(METADATA, options.metadataProfileIds.map(Int::toString)),
        ) { artist, value ->
            val selected = (value as FilterValue.Choice).selected
            artist.metadataProfileId.toString() in selected
        },
        FilterDefinition(FilterSpec.Choice(GENRE, options.genres)) { artist, value ->
            val selected = (value as FilterValue.Choice).selected
            artist.genres.orEmpty().any { it in selected }
        },
        FilterDefinition(FilterSpec.Range(ALBUMS, options.minAlbums, options.maxAlbums)) { artist, value ->
            val range = value as FilterValue.Range
            val min = range.min
            val max = range.max
            val count = albumCountOf(artist, summaries).toDouble()
            (min == null || count >= min) && (max == null || count <= max)
        },
        FilterDefinition(FilterSpec.Choice(FOLDER, options.folders)) { artist, value ->
            val selected = (value as FilterValue.Choice).selected
            folderOf(artist) in selected
        },
        FilterDefinition(FilterSpec.Choice(TAGS, options.tagIds.map(Int::toString))) { artist, value ->
            val selected = (value as FilterValue.Choice).selected
            artist.tags.orEmpty().any { it.toString() in selected }
        },
    )

    /** Number of filters whose value currently narrows the list. */
    fun activeCount(ids: List<String>, state: FilterState): Int = ids.count(state::isActive)
}

/** Sort keys the library offers; selectors stay typed via [SortSpec.byNullable]. */
object ArtistLibrarySorts {

    const val TITLE = "title"
    const val ADDED = "added"
    const val ALBUMS = "albums"
    const val SIZE = "size"

    val ALL: List<String> = listOf(TITLE, ADDED, ALBUMS, SIZE)

    /**
     * [summaries] feeds the album-count sort so tiles and rows order by the
     * same count they display; without one it falls back to artist statistics.
     */
    fun spec(
        id: String,
        summaries: Map<Int, ArtistAlbumSummary> = emptyMap(),
    ): SortSpec<ArtistResource> = when (id) {
        ADDED -> SortSpec.byNullable(ADDED, { ArrTime.parse(it.added)?.toEpochMilli() })
        ALBUMS -> SortSpec.by(ALBUMS, { ArtistLibraryFilters.albumCountOf(it, summaries) })
        SIZE -> SortSpec.byNullable(SIZE, { it.statistics?.sizeOnDisk })
        else -> SortSpec.byNullable(TITLE, { it.sortName ?: it.artistName })
    }
}

/** One rendered window of the presented library, the paging unit the grid asks for. */
data class ArtistPresentation(
    val all: List<ArtistResource>,
    val visible: List<ArtistResource>,
    val endReached: Boolean,
) {
    companion object {
        const val INITIAL_COUNT = 60
        const val PAGE_COUNT = 60

        /** First window into an empty presentation. */
        val EMPTY = ArtistPresentation(emptyList(), emptyList(), true)
    }
}

/** Applies filters, sort, and the paging window; pure so tests cover it end to end. */
fun presentArtists(
    artists: List<ArtistResource>,
    options: ArtistLibraryFilters.Options,
    summaries: Map<Int, ArtistAlbumSummary>,
    filterState: FilterState,
    sortId: String,
    descending: Boolean,
    visibleCount: Int,
): ArtistPresentation {
    val filtered = applyFilters(artists, ArtistLibraryFilters.definitions(options, summaries), filterState)
    val sorted = sortItems(filtered, ArtistLibrarySorts.spec(sortId, summaries), descending)
    val count = visibleCount.coerceAtLeast(0).coerceAtMost(sorted.size)
    return ArtistPresentation(
        all = sorted,
        visible = sorted.subList(0, count),
        endReached = count >= sorted.size,
    )
}
