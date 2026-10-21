package dev.appolon.homelab.arr.feature.sonarr.wanted.data

import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.core.FilterDefinition
import dev.appolon.homelab.arr.core.FilterSpec
import dev.appolon.homelab.arr.core.FilterState
import dev.appolon.homelab.arr.core.FilterValue
import dev.appolon.homelab.arr.core.SortSpec
import dev.appolon.homelab.arr.core.applyFilters
import dev.appolon.homelab.arr.core.sortItems
import kotlinx.serialization.Serializable
import java.util.Locale

/** The two Sonarr wanted lists; each backs one tab and one entity type in the store. */
enum class WantedTab(val wireName: String) {
    MISSING("missing"),
    CUTOFF("cutoff"),
}

/**
 * Server-reported episode totals for both lists, the numbers the shell badges
 * the Wanted entry with. Null until the matching tab finished its first
 * refresh.
 */
@Serializable
data class WantedCounts(
    val missing: Int? = null,
    val cutoff: Int? = null,
)

/**
 * Filter and presentation logic for the wanted episode lists, pure so tests
 * cover it directly. Filter ids: `monitored` (Toggle), `season` (Choice of
 * season numbers). The rendered order is series title, season, episode, so it
 * stays stable no matter which server page an episode arrived on.
 */
object WantedFilters {

    const val MONITORED = "monitored"
    const val SEASON = "season"

    /** Choice option universe: season numbers present in the active list. */
    data class Options(val seasons: List<Int> = emptyList()) {
        fun with(seenSeasons: List<Int>): Options =
            Options(seasons = (seasons + seenSeasons).distinct().sorted())
    }

    fun optionsFrom(episodes: List<EpisodeResource>): Options =
        Options().with(episodes.map { it.seasonNumber })

    /** Specs for the filter controls, in bar order. */
    fun specs(options: Options): List<FilterSpec> = listOf(
        FilterSpec.Toggle(MONITORED),
        FilterSpec.Choice(SEASON, options.seasons.map(Int::toString)),
    )

    /** Matching logic for every spec in [specs]. */
    fun definitions(options: Options): List<FilterDefinition<EpisodeResource>> = listOf(
        FilterDefinition(FilterSpec.Toggle(MONITORED)) { episode, value ->
            (value as FilterValue.Toggle).on?.let { episode.monitored == it } ?: true
        },
        FilterDefinition(
            FilterSpec.Choice(SEASON, options.seasons.map(Int::toString)),
        ) { episode, value ->
            val selected = (value as FilterValue.Choice).selected
            episode.seasonNumber.toString() in selected
        },
    )
}

/** One rendered wanted list after filters; ordering is series, season, episode, id. */
data class WantedPresentation(
    val all: List<EpisodeResource>,
    val endReached: Boolean,
) {
    companion object {
        val EMPTY = WantedPresentation(emptyList(), endReached = true)
    }
}

/** Applies filters and the stable series sort over one cached wanted list. */
fun presentWanted(
    episodes: List<EpisodeResource>,
    options: WantedFilters.Options,
    filterState: FilterState,
): WantedPresentation {
    val filtered = applyFilters(episodes, WantedFilters.definitions(options), filterState)
    val sorted = sortItems(filtered, WANTED_SORT)
    return WantedPresentation(all = sorted, endReached = true)
}

/** Series-first ordering shared by both tabs; ids keep it total and stable. */
private val WANTED_SORT: SortSpec<EpisodeResource> =
    SortSpec.byNullable<EpisodeResource, String>("series", { episode ->
        episode.series.sortTitle?.takeIf { it.isNotBlank() }
            ?: episode.series.title?.takeIf { it.isNotBlank() }
    })
        .then(SortSpec.by<EpisodeResource, Int>("season") { it.seasonNumber })
        .then(SortSpec.by<EpisodeResource, Int>("episode") { it.episodeNumber })
        .then(SortSpec.by<EpisodeResource, Int>("id") { it.id })

/** "S01E05" episode code used by the rows and accessibility labels. */
fun episodeCode(episode: EpisodeResource): String =
    "S%02dE%02d".format(Locale.ROOT, episode.seasonNumber, episode.episodeNumber)

/** Series display title, falling back to the id when the payload carries none. */
fun seriesTitleOf(episode: EpisodeResource): String =
    episode.series.title?.takeIf { it.isNotBlank() } ?: "#${episode.seriesId}"
