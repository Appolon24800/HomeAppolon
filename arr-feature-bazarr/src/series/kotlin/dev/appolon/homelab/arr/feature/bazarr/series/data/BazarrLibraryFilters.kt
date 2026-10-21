package dev.appolon.homelab.arr.feature.bazarr.series.data

import dev.appolon.homelab.arr.core.FilterDefinition
import dev.appolon.homelab.arr.core.FilterSpec
import dev.appolon.homelab.arr.core.FilterState
import dev.appolon.homelab.arr.core.FilterValue
import dev.appolon.homelab.arr.core.SortSpec
import dev.appolon.homelab.arr.core.applyFilters
import dev.appolon.homelab.arr.core.sortItems

/**
 * Filter, sort, and paging presentation for the Bazarr series and movies
 * lists, built on the `:arr-core` Filters framework. Everything here is pure
 * so the ViewModel stays a thin shell and behavior is unit tested directly.
 *
 * Shared filter ids across both kinds: `subtitles` (Choice of code2 values,
 * present or missing), `hearing_impaired` (Toggle: has any HI track; movies
 * only, because the series payload has no subtitle tracks), `monolingual`
 * (Toggle: wants exactly one language), `tags` (Choice of tag strings),
 * `blacklist` (Toggle: has a blacklist entry), `status` (Choice of
 * [SubtitleStatus] names). The web UI offers the same dimensions.
 */
object BazarrLibraryFilters {

    const val SUBTITLES = "subtitles"
    const val HEARING_IMPAIRED = "hearing_impaired"
    const val MONOLINGUAL = "monolingual"
    const val TAGS = "tags"
    const val BLACKLIST = "blacklist"
    const val STATUS = "status"

    /** Filter ids in the order the filter bar shows them. */
    val IDS = listOf(SUBTITLES, HEARING_IMPAIRED, MONOLINGUAL, TAGS, BLACKLIST, STATUS)

    /** The empty value of each filter: the form that narrows nothing. */
    fun emptyValue(id: String): FilterValue = when (id) {
        SUBTITLES, TAGS, STATUS -> FilterValue.Choice(emptySet())
        else -> FilterValue.Toggle(null)
    }

    /**
     * Option universe derived from the library in view plus the joins the
     * filters need: ids with blacklist entries and ids whose profile wants a
     * single language.
     */
    data class Options(
        val languageCodes: List<String> = emptyList(),
        val languageLabels: Map<String, String> = emptyMap(),
        val tags: List<String> = emptyList(),
        val statuses: List<SubtitleStatus> = emptyList(),
        val blacklistedIds: Set<Int> = emptySet(),
        val monolingualIds: Set<Int> = emptySet(),
    ) {
        val statusIds: List<String> get() = statuses.map { it.name }
    }

    /** Options for the series list; [profiles] feeds the monolingual join. */
    fun seriesOptions(
        series: List<BazarrSeries>,
        blacklist: List<BazarrBlacklistEntry>,
        profiles: List<BazarrLanguageProfile>,
    ): Options {
        val monolingualProfiles = monolingualProfileIds(profiles)
        return Options(
            languageCodes = series.flatMap { it.audioLanguages.map(BazarrLanguage::code2) }
                .distinct()
                .sorted(),
            languageLabels = series.flatMap { it.audioLanguages }
                .associate { it.code2 to it.label },
            tags = series.flatMap { it.tags }.distinct().sorted(),
            statuses = series.map { it.status }.distinct().sortedBy { it.ordinal },
            blacklistedIds = blacklist.mapNotNull { it.seriesId }.toSet(),
            monolingualIds = series.filter { it.profileId in monolingualProfiles }
                .map { it.seriesId }
                .toSet(),
        )
    }

    /** Options for the movies list; [profiles] feeds the monolingual join. */
    fun movieOptions(
        movies: List<BazarrMovie>,
        blacklist: List<BazarrBlacklistEntry>,
        profiles: List<BazarrLanguageProfile>,
    ): Options {
        val monolingualProfiles = monolingualProfileIds(profiles)
        return Options(
            languageCodes = movies.flatMap { movie ->
                (movie.subtitles + movie.missingSubtitles).map(BazarrLanguage::code2)
            }.distinct().sorted(),
            languageLabels = movies.flatMap { it.subtitles + it.missingSubtitles }
                .associate { it.code2 to it.label },
            tags = movies.flatMap { it.tags }.distinct().sorted(),
            statuses = movies.map { it.status }.distinct().sortedBy { it.ordinal },
            blacklistedIds = blacklist.mapNotNull { it.radarrId }.toSet(),
            monolingualIds = movies.filter { it.profileId in monolingualProfiles }
                .map { it.radarrId }
                .toSet(),
        )
    }

    /** Profile ids wanting exactly one language; the monolingual definition. */
    fun monolingualProfileIds(profiles: List<BazarrLanguageProfile>): Set<Int> =
        profiles.filter { it.languages.distinct().size == 1 }
            .map { it.profileId }
            .toSet()

    /** Specs for rendering the series filter controls. */
    fun seriesSpecs(options: Options): List<FilterSpec> =
        commonSpecs(options).filterNot { it.id == HEARING_IMPAIRED }

    /** Specs for rendering the movies filter controls; identical shape. */
    fun movieSpecs(options: Options): List<FilterSpec> = commonSpecs(options)

    private fun commonSpecs(options: Options): List<FilterSpec> = listOf(
        FilterSpec.Choice(SUBTITLES, options.languageCodes),
        FilterSpec.Toggle(HEARING_IMPAIRED),
        FilterSpec.Toggle(MONOLINGUAL),
        FilterSpec.Choice(TAGS, options.tags),
        FilterSpec.Toggle(BLACKLIST),
        FilterSpec.Choice(STATUS, options.statusIds),
    )

    /**
     * Matching logic for every series spec, over [BazarrSeries]. The hearing
     * impaired filter is intentionally absent: the series payload carries no
     * subtitle tracks, the HI flag lives on episodes, and the episode browser
     * surfaces it there instead of matching on data the list does not have.
     */
    fun seriesDefinitions(options: Options): List<FilterDefinition<BazarrSeries>> = listOf(
        FilterDefinition(FilterSpec.Choice(SUBTITLES, options.languageCodes)) { item, value ->
            val selected = (value as FilterValue.Choice).selected
            item.audioLanguages.any { it.code2 in selected }
        },
        FilterDefinition(FilterSpec.Toggle(MONOLINGUAL)) { item, value ->
            matchesMonolingual((value as FilterValue.Toggle).on, item.profileId, item.seriesId, options)
        },
        FilterDefinition(FilterSpec.Choice(TAGS, options.tags)) { item, value ->
            val selected = (value as FilterValue.Choice).selected
            item.tags.any { it in selected }
        },
        FilterDefinition(FilterSpec.Toggle(BLACKLIST)) { item, value ->
            matchesToggle((value as FilterValue.Toggle).on) {
                item.seriesId in options.blacklistedIds
            }
        },
        FilterDefinition(FilterSpec.Choice(STATUS, options.statusIds)) { item, value ->
            (value as FilterValue.Choice).selected.contains(item.status.name)
        },
    )

    /** Matching logic for every movies spec, over [BazarrMovie]. */
    fun movieDefinitions(options: Options): List<FilterDefinition<BazarrMovie>> = listOf(
        FilterDefinition(FilterSpec.Choice(SUBTITLES, options.languageCodes)) { item, value ->
            val selected = (value as FilterValue.Choice).selected
            (item.subtitles + item.missingSubtitles).any { it.code2 in selected }
        },
        FilterDefinition(FilterSpec.Toggle(HEARING_IMPAIRED)) { item, value ->
            matchesToggle((value as FilterValue.Toggle).on) {
                item.subtitles.any { it.hearingImpaired }
            }
        },
        FilterDefinition(FilterSpec.Toggle(MONOLINGUAL)) { item, value ->
            matchesMonolingual((value as FilterValue.Toggle).on, item.profileId, item.radarrId, options)
        },
        FilterDefinition(FilterSpec.Choice(TAGS, options.tags)) { item, value ->
            val selected = (value as FilterValue.Choice).selected
            item.tags.any { it in selected }
        },
        FilterDefinition(FilterSpec.Toggle(BLACKLIST)) { item, value ->
            matchesToggle((value as FilterValue.Toggle).on) {
                item.radarrId in options.blacklistedIds
            }
        },
        FilterDefinition(FilterSpec.Choice(STATUS, options.statusIds)) { item, value ->
            (value as FilterValue.Choice).selected.contains(item.status.name)
        },
    )

    private fun matchesMonolingual(
        on: Boolean?,
        profileId: Int?,
        itemId: Int,
        options: Options,
    ): Boolean = on?.let { wanted -> (itemId in options.monolingualIds) == wanted } ?: true

    private inline fun matchesToggle(on: Boolean?, predicate: () -> Boolean): Boolean =
        on?.let { predicate() == it } ?: true
}

/** Sort keys the series list offers. */
object BazarrSeriesSorts {

    const val TITLE = "title"
    const val YEAR = "year"
    const val MISSING = "missing"
    const val EPISODES = "episodes"

    val ALL: List<String> = listOf(TITLE, YEAR, MISSING, EPISODES)

    fun spec(id: String): SortSpec<BazarrSeries> = when (id) {
        YEAR -> SortSpec.byNullable(YEAR, { it.yearValue })
        MISSING -> SortSpec.by(MISSING, { it.episodeMissingCount })
        EPISODES -> SortSpec.by(EPISODES, { it.episodeFileCount })
        else -> SortSpec.by(TITLE, { it.title.lowercase() })
    }
}

/** Sort keys the movies list offers. */
object BazarrMovieSorts {

    const val TITLE = "title"
    const val YEAR = "year"
    const val MISSING = "missing"
    const val SUBTITLES = "subtitles"

    val ALL: List<String> = listOf(TITLE, YEAR, MISSING, SUBTITLES)

    fun spec(id: String): SortSpec<BazarrMovie> = when (id) {
        YEAR -> SortSpec.byNullable(YEAR, { it.yearValue })
        MISSING -> SortSpec.by(MISSING, { it.missingSubtitles.size })
        SUBTITLES -> SortSpec.by(SUBTITLES, { it.subtitles.size })
        else -> SortSpec.by(TITLE, { it.title.lowercase() })
    }
}

/** One rendered window of the presented list, the paging unit the lazy lists ask for. */
data class BazarrPresentation<T>(
    val all: List<T>,
    val visible: List<T>,
    val endReached: Boolean,
) {
    companion object {
        const val INITIAL_COUNT = 60
        const val PAGE_COUNT = 60

        /** First window into an empty presentation. */
        val EMPTY = BazarrPresentation<Nothing>(emptyList(), emptyList(), true)
    }
}

/** Applies filters, sort, and the paging window to the series list; pure. */
fun presentSeries(
    series: List<BazarrSeries>,
    options: BazarrLibraryFilters.Options,
    filterState: FilterState,
    sortId: String,
    descending: Boolean,
    visibleCount: Int,
): BazarrPresentation<BazarrSeries> {
    val filtered = applyFilters(
        series,
        BazarrLibraryFilters.seriesDefinitions(options),
        filterState,
    )
    val sorted = sortItems(filtered, BazarrSeriesSorts.spec(sortId), descending)
    return window(sorted, visibleCount)
}

/** Applies filters, sort, and the paging window to the movies list; pure. */
fun presentMovies(
    movies: List<BazarrMovie>,
    options: BazarrLibraryFilters.Options,
    filterState: FilterState,
    sortId: String,
    descending: Boolean,
    visibleCount: Int,
): BazarrPresentation<BazarrMovie> {
    val filtered = applyFilters(
        movies,
        BazarrLibraryFilters.movieDefinitions(options),
        filterState,
    )
    val sorted = sortItems(filtered, BazarrMovieSorts.spec(sortId), descending)
    return window(sorted, visibleCount)
}

private fun <T> window(sorted: List<T>, visibleCount: Int): BazarrPresentation<T> {
    val count = visibleCount.coerceAtLeast(0).coerceAtMost(sorted.size)
    return BazarrPresentation(
        all = sorted,
        visible = sorted.subList(0, count),
        endReached = count >= sorted.size,
    )
}
