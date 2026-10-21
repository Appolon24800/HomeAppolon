package dev.appolon.homelab.arr.feature.sonarr.library.data

import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.FilterState
import dev.appolon.homelab.arr.core.FilterValue
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.EntityStore
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/**
 * One locally saved custom filter. Sonarr also has server-side custom
 * filters; these are device-local so they work offline and never sync, per
 * the feature brief. The values mirror the library's filter ids.
 */
@Serializable
data class SavedSeriesFilter(
    val id: String,
    val label: String,
    val monitored: Boolean? = null,
    val missing: Boolean? = null,
    val quality: Set<String> = emptySet(),
    val genre: Set<String> = emptySet(),
    val network: Set<String> = emptySet(),
    val folder: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val seasonMin: Double? = null,
    val seasonMax: Double? = null,
    val yearMin: Double? = null,
    val yearMax: Double? = null,
)

/** Converts a saved filter into the framework state the presenter consumes. */
fun SavedSeriesFilter.toFilterState(): FilterState {
    var state = FilterState()
    monitored?.let { state = state.with(SeriesLibraryFilters.MONITORED, FilterValue.Toggle(it)) }
    missing?.let { state = state.with(SeriesLibraryFilters.MISSING, FilterValue.Toggle(it)) }
    if (quality.isNotEmpty()) {
        state = state.with(SeriesLibraryFilters.QUALITY, FilterValue.Choice(quality))
    }
    if (genre.isNotEmpty()) {
        state = state.with(SeriesLibraryFilters.GENRE, FilterValue.Choice(genre))
    }
    if (network.isNotEmpty()) {
        state = state.with(SeriesLibraryFilters.NETWORK, FilterValue.Choice(network))
    }
    if (folder.isNotEmpty()) {
        state = state.with(SeriesLibraryFilters.FOLDER, FilterValue.Choice(folder))
    }
    if (tags.isNotEmpty()) {
        state = state.with(SeriesLibraryFilters.TAGS, FilterValue.Choice(tags))
    }
    if (seasonMin != null || seasonMax != null) {
        state = state.with(SeriesLibraryFilters.SEASONS, FilterValue.Range(seasonMin, seasonMax))
    }
    if (yearMin != null || yearMax != null) {
        state = state.with(SeriesLibraryFilters.YEAR, FilterValue.Range(yearMin, yearMax))
    }
    return state
}

/** Converts live filter state into the serializable saved form. */
fun FilterState.toSavedValues(): SavedSeriesFilterValues {
    fun choice(id: String): Set<String> =
        (values[id] as? FilterValue.Choice)?.selected ?: emptySet()

    val seasons = values[SeriesLibraryFilters.SEASONS] as? FilterValue.Range
    val year = values[SeriesLibraryFilters.YEAR] as? FilterValue.Range
    return SavedSeriesFilterValues(
        monitored = (values[SeriesLibraryFilters.MONITORED] as? FilterValue.Toggle)?.on,
        missing = (values[SeriesLibraryFilters.MISSING] as? FilterValue.Toggle)?.on,
        quality = choice(SeriesLibraryFilters.QUALITY),
        genre = choice(SeriesLibraryFilters.GENRE),
        network = choice(SeriesLibraryFilters.NETWORK),
        folder = choice(SeriesLibraryFilters.FOLDER),
        tags = choice(SeriesLibraryFilters.TAGS),
        seasonMin = seasons?.min,
        seasonMax = seasons?.max,
        yearMin = year?.min,
        yearMax = year?.max,
    )
}

/** Value half of [toSavedValues], kept separate so ids stay internal. */
@Serializable
data class SavedSeriesFilterValues(
    val monitored: Boolean? = null,
    val missing: Boolean? = null,
    val quality: Set<String> = emptySet(),
    val genre: Set<String> = emptySet(),
    val network: Set<String> = emptySet(),
    val folder: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val seasonMin: Double? = null,
    val seasonMax: Double? = null,
    val yearMin: Double? = null,
    val yearMax: Double? = null,
)

/** Builds a saved filter from a label and live state, assigning a fresh id. */
fun newSavedFilter(label: String, state: FilterState): SavedSeriesFilter {
    val values = state.toSavedValues()
    return SavedSeriesFilter(
        id = UUID.randomUUID().toString(),
        label = label,
        monitored = values.monitored,
        missing = values.missing,
        quality = values.quality,
        genre = values.genre,
        network = values.network,
        folder = values.folder,
        tags = values.tags,
        seasonMin = values.seasonMin,
        seasonMax = values.seasonMax,
        yearMin = values.yearMin,
        yearMax = values.yearMax,
    )
}

/**
 * Saved custom filters persisted through the committed [EntityStore], one row
 * per filter, scoped to the server whose library they filter. Adds no storage
 * mechanism of its own.
 */
class SavedSeriesFiltersStore(
    private val store: EntityStore,
    private val serverId: ServerId,
) {
    fun observe(): Flow<List<SavedSeriesFilter>> =
        store.observe(serverId, TYPE, SavedSeriesFilter.serializer())
            .map { rows -> rows.sortedBy { it.label.lowercase() } }
            .distinctUntilChanged()

    suspend fun save(filter: SavedSeriesFilter) {
        store.put(serverId, TYPE, filter.id, filter, SavedSeriesFilter.serializer())
    }

    suspend fun delete(id: String) {
        store.delete(serverId, TYPE, id)
    }

    private companion object {
        val TYPE = EntityType("sonarr.savedfilter")
    }
}
