package dev.appolon.homelab.arr.feature.radarr.library.data

import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.core.FilterDefinition
import dev.appolon.homelab.arr.core.FilterSpec
import dev.appolon.homelab.arr.core.FilterValue
import dev.appolon.homelab.arr.core.SortSpec
import dev.appolon.homelab.arr.core.applyFilters
import dev.appolon.homelab.arr.core.sortItems

/**
 * Filter, sort, and paging presentation for the movies library, built on the
 * `:arr-core` Filters framework. Everything here is pure so the ViewModel is a
 * thin shell and behavior is unit tested directly.
 *
 * Filter ids: `monitored` (Toggle), `missing` (Toggle), `quality` (Choice of
 * profile ids), `genre` (Choice), `year` (Range), `folder` (Choice), `tags`
 * (Choice of tag ids). Choice options are strings so profile and tag ids ride
 * the same [FilterValue.Choice] shape.
 */
object MovieLibraryFilters {

    const val MONITORED = "monitored"
    const val MISSING = "missing"
    const val QUALITY = "quality"
    const val GENRE = "genre"
    const val YEAR = "year"
    const val FOLDER = "folder"
    const val TAGS = "tags"

    /** Year used by fresh TMDB entries that carry no release year yet. */
    const val UNKNOWN_YEAR = 0

    /** Option universe derived from the library itself when profiles are unavailable. */
    data class Options(
        val genres: List<String> = emptyList(),
        val folders: List<String> = emptyList(),
        val tagIds: List<Int> = emptyList(),
        val qualityProfileIds: List<Int> = emptyList(),
        val minYear: Double = 1900.0,
        val maxYear: Double = 2030.0,
    ) {
        val yearSpan: ClosedFloatingPointRange<Double> get() = minYear..maxYear
    }

    /** Extracts every choice option plus the year span from the movies in view. */
    fun optionsFrom(movies: List<MovieResource>, nowYear: Int): Options {
        val years = movies.map { it.year }.filter { it > UNKNOWN_YEAR }
        return Options(
            genres = movies.flatMap { it.genres.orEmpty() }.distinct().sorted(),
            folders = movies.mapNotNull { folderOf(it) }.distinct().sorted(),
            tagIds = movies.flatMap { it.tags.orEmpty() }.distinct().sorted(),
            qualityProfileIds = movies.map { it.qualityProfileId }.distinct().sorted(),
            minYear = (years.minOrNull() ?: (nowYear - 40)).toDouble(),
            maxYear = (years.maxOrNull() ?: nowYear).toDouble(),
        )
    }

    /** The folder a movie lives in, preferring the explicit fields over the path. */
    fun folderOf(movie: MovieResource): String? =
        movie.folder ?: movie.rootFolderPath ?: movie.path?.substringAfterLast('/')

    /** True when a movie is monitored but has no file, the "missing" notion every list shares. */
    fun isMissing(movie: MovieResource): Boolean = movie.monitored && movie.hasFile != true

    /** Specs for rendering filter controls, in the order the filter bar shows them. */
    fun specs(options: Options): List<FilterSpec> = listOf(
        FilterSpec.Toggle(MONITORED),
        FilterSpec.Toggle(MISSING),
        FilterSpec.Choice(QUALITY, options.qualityProfileIds.map(Int::toString)),
        FilterSpec.Choice(GENRE, options.genres),
        FilterSpec.Range(YEAR, options.minYear, options.maxYear),
        FilterSpec.Choice(FOLDER, options.folders),
        FilterSpec.Choice(TAGS, options.tagIds.map(Int::toString)),
    )

    /** Matching logic for every spec in [specs], over [MovieResource]. */
    fun definitions(options: Options): List<FilterDefinition<MovieResource>> = listOf(
        FilterDefinition(FilterSpec.Toggle(MONITORED)) { movie, value ->
            (value as FilterValue.Toggle).on?.let { movie.monitored == it } ?: true
        },
        FilterDefinition(FilterSpec.Toggle(MISSING)) { movie, value ->
            (value as FilterValue.Toggle).on?.let { isMissing(movie) == it } ?: true
        },
        FilterDefinition(
            FilterSpec.Choice(QUALITY, options.qualityProfileIds.map(Int::toString)),
        ) { movie, value ->
            val selected = (value as FilterValue.Choice).selected
            movie.qualityProfileId.toString() in selected
        },
        FilterDefinition(FilterSpec.Choice(GENRE, options.genres)) { movie, value ->
            val selected = (value as FilterValue.Choice).selected
            movie.genres.orEmpty().any { it in selected }
        },
        FilterDefinition(FilterSpec.Range(YEAR, options.minYear, options.maxYear)) { movie, value ->
            val range = value as FilterValue.Range
            val min = range.min
            val max = range.max
            if (movie.year <= UNKNOWN_YEAR) {
                false
            } else {
                (min == null || movie.year >= min) && (max == null || movie.year <= max)
            }
        },
        FilterDefinition(FilterSpec.Choice(FOLDER, options.folders)) { movie, value ->
            val selected = (value as FilterValue.Choice).selected
            folderOf(movie) in selected
        },
        FilterDefinition(FilterSpec.Choice(TAGS, options.tagIds.map(Int::toString))) { movie, value ->
            val selected = (value as FilterValue.Choice).selected
            movie.tags.orEmpty().any { it.toString() in selected }
        },
    )

    /** Number of filters whose value currently narrows the list. */
    fun activeCount(ids: List<String>, state: dev.appolon.homelab.arr.core.FilterState): Int =
        ids.count(state::isActive)
}

/** Sort keys the library offers; selectors stay typed via [SortSpec.byNullable]. */
object MovieLibrarySorts {

    const val TITLE = "title"
    const val YEAR = "year"
    const val ADDED = "added"
    const val SIZE = "size"

    val ALL: List<String> = listOf(TITLE, YEAR, ADDED, SIZE)

    fun spec(id: String): SortSpec<MovieResource> = when (id) {
        YEAR -> SortSpec.byNullable(YEAR, { it.year.takeIf { y -> y > 0 } })
        ADDED -> SortSpec.byNullable(
            ADDED,
            { movie -> dev.appolon.homelab.arr.core.ArrTime.parse(movie.added)?.toEpochMilli() },
        )
        SIZE -> SortSpec.byNullable(
            SIZE,
            { movie ->
                (movie.statistics?.sizeOnDisk ?: movie.sizeOnDisk)?.takeIf { it > 0 }
            },
        )
        else -> SortSpec.byNullable(TITLE, { it.sortTitle ?: it.title })
    }
}

/** One rendered window of the presented library, the paging unit the grid asks for. */
data class MoviePresentation(
    val all: List<MovieResource>,
    val visible: List<MovieResource>,
    val endReached: Boolean,
) {
    companion object {
        const val INITIAL_COUNT = 60
        const val PAGE_COUNT = 60

        /** First window into an empty presentation. */
        val EMPTY = MoviePresentation(emptyList(), emptyList(), true)
    }
}

/** Applies filters, sort, and the paging window; pure so tests cover it end to end. */
fun presentMovies(
    movies: List<MovieResource>,
    options: MovieLibraryFilters.Options,
    filterState: dev.appolon.homelab.arr.core.FilterState,
    sortId: String,
    descending: Boolean,
    visibleCount: Int,
): MoviePresentation {
    val filtered = applyFilters(movies, MovieLibraryFilters.definitions(options), filterState)
    val sorted = sortItems(filtered, MovieLibrarySorts.spec(sortId), descending)
    val count = visibleCount.coerceAtLeast(0).coerceAtMost(sorted.size)
    return MoviePresentation(
        all = sorted,
        visible = sorted.subList(0, count),
        endReached = count >= sorted.size,
    )
}
