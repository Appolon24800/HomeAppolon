package dev.appolon.homelab.arr.feature.radarr.library.data

import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.FilterState
import dev.appolon.homelab.arr.core.FilterValue
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.EntityStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * One locally saved custom filter. Radarr also has server-side custom
 * filters; these are device-local so they work offline and never sync, per
 * the feature brief. The values mirror the library's filter ids.
 */
@Serializable
data class SavedMovieFilter(
    val id: String,
    val label: String,
    val monitored: Boolean? = null,
    val missing: Boolean? = null,
    val quality: Set<String> = emptySet(),
    val genre: Set<String> = emptySet(),
    val folder: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val yearMin: Double? = null,
    val yearMax: Double? = null,
)

/** Converts a saved filter into the framework state the presenter consumes. */
fun SavedMovieFilter.toFilterState(): FilterState {
    var state = FilterState()
    monitored?.let { state = state.with(MovieLibraryFilters.MONITORED, FilterValue.Toggle(it)) }
    missing?.let { state = state.with(MovieLibraryFilters.MISSING, FilterValue.Toggle(it)) }
    if (quality.isNotEmpty()) {
        state = state.with(MovieLibraryFilters.QUALITY, FilterValue.Choice(quality))
    }
    if (genre.isNotEmpty()) {
        state = state.with(MovieLibraryFilters.GENRE, FilterValue.Choice(genre))
    }
    if (folder.isNotEmpty()) {
        state = state.with(MovieLibraryFilters.FOLDER, FilterValue.Choice(folder))
    }
    if (tags.isNotEmpty()) {
        state = state.with(MovieLibraryFilters.TAGS, FilterValue.Choice(tags))
    }
    if (yearMin != null || yearMax != null) {
        state = state.with(MovieLibraryFilters.YEAR, FilterValue.Range(yearMin, yearMax))
    }
    return state
}

/** Converts live filter state into the serializable saved form. */
fun FilterState.toSavedValues(): SavedMovieFilterValues {
    fun choice(id: String): Set<String> =
        (values[id] as? FilterValue.Choice)?.selected ?: emptySet()
    val range = values[MovieLibraryFilters.YEAR] as? FilterValue.Range
    return SavedMovieFilterValues(
        monitored = (values[MovieLibraryFilters.MONITORED] as? FilterValue.Toggle)?.on,
        missing = (values[MovieLibraryFilters.MISSING] as? FilterValue.Toggle)?.on,
        quality = choice(MovieLibraryFilters.QUALITY),
        genre = choice(MovieLibraryFilters.GENRE),
        folder = choice(MovieLibraryFilters.FOLDER),
        tags = choice(MovieLibraryFilters.TAGS),
        yearMin = range?.min,
        yearMax = range?.max,
    )
}

/** Value half of [toSavedValues], kept separate so ids stay internal. */
@Serializable
data class SavedMovieFilterValues(
    val monitored: Boolean? = null,
    val missing: Boolean? = null,
    val quality: Set<String> = emptySet(),
    val genre: Set<String> = emptySet(),
    val folder: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val yearMin: Double? = null,
    val yearMax: Double? = null,
)

/** Builds a saved filter from a label and live state, assigning a fresh id. */
fun newSavedFilter(label: String, state: FilterState): SavedMovieFilter {
    val values = state.toSavedValues()
    return SavedMovieFilter(
        id = UUID.randomUUID().toString(),
        label = label,
        monitored = values.monitored,
        missing = values.missing,
        quality = values.quality,
        genre = values.genre,
        folder = values.folder,
        tags = values.tags,
        yearMin = values.yearMin,
        yearMax = values.yearMax,
    )
}

/**
 * Saved custom filters persisted through the committed [EntityStore], one row
 * per filter, scoped to the server whose library they filter. Adds no storage
 * mechanism of its own.
 */
class SavedMovieFiltersStore(
    private val store: EntityStore,
    private val serverId: ServerId,
) {
    fun observe(): Flow<List<SavedMovieFilter>> =
        store.observe(serverId, TYPE, SavedMovieFilter.serializer())
            .map { rows -> rows.sortedBy { it.label.lowercase() } }
            .distinctUntilChanged()

    suspend fun save(filter: SavedMovieFilter) {
        store.put(serverId, TYPE, filter.id, filter, SavedMovieFilter.serializer())
    }

    suspend fun delete(id: String) {
        store.delete(serverId, TYPE, id)
    }

    private companion object {
        val TYPE = EntityType("radarr.savedfilter")
    }
}
