package dev.appolon.homelab.arr.feature.radarr.wanted.data

import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.core.FilterDefinition
import dev.appolon.homelab.arr.core.FilterSpec
import dev.appolon.homelab.arr.core.FilterValue
import dev.appolon.homelab.arr.core.SortSpec
import dev.appolon.homelab.arr.core.applyFilters
import dev.appolon.homelab.arr.core.sortItems
import kotlinx.serialization.Serializable

/** The two Wanted lists; each backs one tab and one entity type in the store. */
enum class WantedTab(val wireName: String) {
    MISSING("missing"),
    CUTOFF("cutoff"),
}

/**
 * Server-reported totals for both lists, the numbers the shell badges the
 * Wanted entry with. Null until the matching tab finished its first refresh.
 */
@Serializable
data class WantedCounts(
    val missing: Int? = null,
    val cutoff: Int? = null,
)

/**
 * Filter and presentation logic for the wanted lists, pure so tests cover it
 * directly. Filter ids: `monitored` (Toggle), `quality` (Choice of profile
 * ids). The list itself is always sorted by title so the rendered order stays
 * stable no matter which server page an item arrived on.
 */
object WantedFilters {

    const val MONITORED = "monitored"
    const val QUALITY = "quality"

    /** Choice option universe: profile ids seen in the list plus known profiles. */
    data class Options(val qualityProfileIds: List<Int> = emptyList()) {
        fun with(movieProfileIds: List<Int>, knownProfileIds: List<Int>): Options = Options(
            qualityProfileIds = (
                qualityProfileIds + movieProfileIds + knownProfileIds
                ).distinct().sorted(),
        )
    }

    fun optionsFrom(
        movies: List<MovieResource>,
        knownProfiles: List<Int>,
    ): Options = Options().with(movies.map { it.qualityProfileId }, knownProfiles)

    /** Specs for the filter controls, in bar order. */
    fun specs(options: Options): List<FilterSpec> = listOf(
        FilterSpec.Toggle(MONITORED),
        FilterSpec.Choice(QUALITY, options.qualityProfileIds.map(Int::toString)),
    )

    /** Matching logic for every spec in [specs]. */
    fun definitions(options: Options): List<FilterDefinition<MovieResource>> = listOf(
        FilterDefinition(FilterSpec.Toggle(MONITORED)) { movie, value ->
            (value as FilterValue.Toggle).on?.let { movie.monitored == it } ?: true
        },
        FilterDefinition(
            FilterSpec.Choice(QUALITY, options.qualityProfileIds.map(Int::toString)),
        ) { movie, value ->
            val selected = (value as FilterValue.Choice).selected
            movie.qualityProfileId.toString() in selected
        },
    )
}

/** One rendered wanted list after filters; ordering is title, then year, then id. */
data class WantedPresentation(
    val all: List<MovieResource>,
    val endReached: Boolean,
) {
    companion object {
        val EMPTY = WantedPresentation(emptyList(), endReached = true)
    }
}

/** Applies filters and the stable title sort over one cached wanted list. */
fun presentWanted(
    movies: List<MovieResource>,
    options: WantedFilters.Options,
    filterState: dev.appolon.homelab.arr.core.FilterState,
): WantedPresentation {
    val filtered = applyFilters(movies, WantedFilters.definitions(options), filterState)
    val sorted = sortItems(filtered, WANTED_SORT)
    return WantedPresentation(all = sorted, endReached = true)
}

/** Title-first ordering shared by both tabs; ids keep it total and stable. */
private val WANTED_SORT: SortSpec<MovieResource> =
    SortSpec.byNullable<MovieResource, String>("title", { it.sortTitle ?: it.title })
        .then(SortSpec.byNullable<MovieResource, Int>("year", { it.year.takeIf { y -> y > 0 } }))
        .then(SortSpec.by<MovieResource, Int>("id") { it.id })
