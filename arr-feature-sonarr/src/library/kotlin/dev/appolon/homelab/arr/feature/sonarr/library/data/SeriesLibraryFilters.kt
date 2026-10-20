package dev.appolon.homelab.arr.feature.sonarr.library.data

import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.core.ArrTime
import dev.appolon.homelab.arr.core.FilterDefinition
import dev.appolon.homelab.arr.core.FilterSpec
import dev.appolon.homelab.arr.core.FilterState
import dev.appolon.homelab.arr.core.FilterValue
import dev.appolon.homelab.arr.core.SortSpec
import dev.appolon.homelab.arr.core.applyFilters
import dev.appolon.homelab.arr.core.sortItems

/**
 * Filter, sort, and paging presentation for the series library, built on the
 * `:arr-core` Filters framework. Everything here is pure so the ViewModel is a
 * thin shell and behavior is unit tested directly.
 *
 * Filter ids: `monitored` (Toggle), `missing` (Toggle), `quality` (Choice of
 * profile ids), `genre` (Choice), `network` (Choice), `seasons` (Range of
 * season counts), `year` (Range), `folder` (Choice), `tags` (Choice of tag
 * ids). Choice options are strings so profile and tag ids ride the same
 * [FilterValue.Choice] shape.
 */
object SeriesLibraryFilters {

    const val MONITORED = "monitored"
    const val MISSING = "missing"
    const val QUALITY = "quality"
    const val GENRE = "genre"
    const val NETWORK = "network"
    const val SEASONS = "seasons"
    const val YEAR = "year"
    const val FOLDER = "folder"
    const val TAGS = "tags"

    /** Year used by fresh TVDB entries that carry no air year yet. */
    const val UNKNOWN_YEAR = 0

    /** Option universe derived from the library itself when lookups are unavailable. */
    data class Options(
        val genres: List<String> = emptyList(),
        val networks: List<String> = emptyList(),
        val folders: List<String> = emptyList(),
        val tagIds: List<Int> = emptyList(),
        val qualityProfileIds: List<Int> = emptyList(),
        val minYear: Double = 1940.0,
        val maxYear: Double = 2030.0,
        val minSeasons: Double = 0.0,
        val maxSeasons: Double = 50.0,
    ) {
        val yearSpan: ClosedFloatingPointRange<Double> get() = minYear..maxYear
        val seasonSpan: ClosedFloatingPointRange<Double> get() = minSeasons..maxSeasons
    }

    /** Extracts every choice option plus the year and season spans from the series in view. */
    fun optionsFrom(series: List<SeriesResource>, nowYear: Int): Options {
        val years = series.map { it.year }.filter { it > UNKNOWN_YEAR }
        val seasonCounts = series.map { seasonCountOf(it) }
        return Options(
            genres = series.flatMap { it.genres.orEmpty() }.distinct().sorted(),
            networks = series.mapNotNull { it.network?.trim()?.takeIf(String::isNotEmpty) }
                .distinct().sorted(),
            folders = series.mapNotNull { folderOf(it) }.distinct().sorted(),
            tagIds = series.flatMap { it.tags.orEmpty() }.distinct().sorted(),
            qualityProfileIds = series.map { it.qualityProfileId }.distinct().sorted(),
            minYear = (years.minOrNull() ?: (nowYear - 60)).toDouble(),
            maxYear = (years.maxOrNull() ?: nowYear).toDouble(),
            minSeasons = (seasonCounts.minOrNull() ?: 0).toDouble(),
            maxSeasons = (seasonCounts.maxOrNull() ?: 1).coerceAtLeast(1).toDouble(),
        )
    }

    /** The folder a series lives in, preferring the explicit fields over the path. */
    fun folderOf(series: SeriesResource): String? =
        series.folder ?: series.rootFolderPath ?: series.path?.trimEnd('/')?.substringAfterLast('/')

    /** Season count as Sonarr reports it, with the seasons list as fallback. */
    fun seasonCountOf(series: SeriesResource): Int =
        series.statistics.seasonCount.takeIf { it > 0 } ?: series.seasons.orEmpty().size

    /**
     * Missing monitored episodes: the difference between monitored episode
     * count and files on disk, per the feature matrix's badge rule. Never
     * negative, because a stale cache can briefly report more files.
     */
    fun missingEpisodeCount(series: SeriesResource): Int =
        (series.statistics.episodeCount - series.statistics.episodeFileCount).coerceAtLeast(0)

    /** True when a monitored series still misses episodes; unmonitored series never miss. */
    fun isMissing(series: SeriesResource): Boolean =
        series.monitored && missingEpisodeCount(series) > 0

    /** Specs for rendering filter controls, in the order the filter bar shows them. */
    fun specs(options: Options): List<FilterSpec> = listOf(
        FilterSpec.Toggle(MONITORED),
        FilterSpec.Toggle(MISSING),
        FilterSpec.Choice(QUALITY, options.qualityProfileIds.map(Int::toString)),
        FilterSpec.Choice(GENRE, options.genres),
        FilterSpec.Choice(NETWORK, options.networks),
        FilterSpec.Range(SEASONS, options.minSeasons, options.maxSeasons),
        FilterSpec.Range(YEAR, options.minYear, options.maxYear),
        FilterSpec.Choice(FOLDER, options.folders),
        FilterSpec.Choice(TAGS, options.tagIds.map(Int::toString)),
    )

    /** Matching logic for every spec in [specs], over [SeriesResource]. */
    fun definitions(options: Options): List<FilterDefinition<SeriesResource>> = listOf(
        FilterDefinition(FilterSpec.Toggle(MONITORED)) { series, value ->
            (value as FilterValue.Toggle).on?.let { series.monitored == it } ?: true
        },
        FilterDefinition(FilterSpec.Toggle(MISSING)) { series, value ->
            (value as FilterValue.Toggle).on?.let { isMissing(series) == it } ?: true
        },
        FilterDefinition(
            FilterSpec.Choice(QUALITY, options.qualityProfileIds.map(Int::toString)),
        ) { series, value ->
            val selected = (value as FilterValue.Choice).selected
            series.qualityProfileId.toString() in selected
        },
        FilterDefinition(FilterSpec.Choice(GENRE, options.genres)) { series, value ->
            val selected = (value as FilterValue.Choice).selected
            series.genres.orEmpty().any { it in selected }
        },
        FilterDefinition(FilterSpec.Choice(NETWORK, options.networks)) { series, value ->
            val selected = (value as FilterValue.Choice).selected
            series.network?.trim()?.takeIf(String::isNotEmpty) in selected
        },
        FilterDefinition(FilterSpec.Range(SEASONS, options.minSeasons, options.maxSeasons)) {
            series, value ->
            val range = value as FilterValue.Range
            val min = range.min
            val max = range.max
            val count = seasonCountOf(series).toDouble()
            (min == null || count >= min) && (max == null || count <= max)
        },
        FilterDefinition(FilterSpec.Range(YEAR, options.minYear, options.maxYear)) { series, value ->
            val range = value as FilterValue.Range
            val min = range.min
            val max = range.max
            if (series.year <= UNKNOWN_YEAR) {
                false
            } else {
                (min == null || series.year >= min) && (max == null || series.year <= max)
            }
        },
        FilterDefinition(FilterSpec.Choice(FOLDER, options.folders)) { series, value ->
            val selected = (value as FilterValue.Choice).selected
            folderOf(series) in selected
        },
        FilterDefinition(FilterSpec.Choice(TAGS, options.tagIds.map(Int::toString))) {
            series, value ->
            val selected = (value as FilterValue.Choice).selected
            series.tags.orEmpty().any { it.toString() in selected }
        },
    )

    /** Number of filters whose value currently narrows the list. */
    fun activeCount(ids: List<String>, state: FilterState): Int = ids.count(state::isActive)
}

/** Sort keys the library offers; selectors stay typed via [SortSpec.byNullable]. */
object SeriesLibrarySorts {

    const val TITLE = "title"
    const val YEAR = "year"
    const val SEASONS = "seasons"
    const val ADDED = "added"
    const val SIZE = "size"

    val ALL: List<String> = listOf(TITLE, YEAR, SEASONS, ADDED, SIZE)

    fun spec(id: String): SortSpec<SeriesResource> = when (id) {
        YEAR -> SortSpec.byNullable(YEAR, { it.year.takeIf { y -> y > 0 } })
        SEASONS -> SortSpec.by(SEASONS, { SeriesLibraryFilters.seasonCountOf(it) })
        ADDED -> SortSpec.byNullable(ADDED, { s -> ArrTime.parse(s.added)?.toEpochMilli() })
        SIZE -> SortSpec.byNullable(SIZE, { it.statistics.sizeOnDisk })
        else -> SortSpec.byNullable(TITLE, { it.sortTitle ?: it.title })
    }
}

/** One rendered window of the presented library, the paging unit the lazy lists ask for. */
data class SeriesPresentation(
    val all: List<SeriesResource>,
    val visible: List<SeriesResource>,
    val endReached: Boolean,
) {
    companion object {
        const val INITIAL_COUNT = 60
        const val PAGE_COUNT = 60

        /** First window into an empty presentation. */
        val EMPTY = SeriesPresentation(emptyList(), emptyList(), true)
    }
}

/** Applies filters, sort, and the paging window; pure so tests cover it end to end. */
fun presentSeries(
    series: List<SeriesResource>,
    options: SeriesLibraryFilters.Options,
    filterState: FilterState,
    sortId: String,
    descending: Boolean,
    visibleCount: Int,
): SeriesPresentation {
    val filtered = applyFilters(series, SeriesLibraryFilters.definitions(options), filterState)
    val sorted = sortItems(filtered, SeriesLibrarySorts.spec(sortId), descending)
    val count = visibleCount.coerceAtLeast(0).coerceAtMost(sorted.size)
    return SeriesPresentation(
        all = sorted,
        visible = sorted.subList(0, count),
        endReached = count >= sorted.size,
    )
}
