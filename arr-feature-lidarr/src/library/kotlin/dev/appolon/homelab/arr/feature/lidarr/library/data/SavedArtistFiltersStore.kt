package dev.appolon.homelab.arr.feature.lidarr.library.data

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
 * One locally saved custom filter. Lidarr also has server-side custom filters;
 * these are device-local so they work offline and never sync, per the feature
 * brief. The values mirror the library's filter ids.
 */
@Serializable
data class SavedArtistFilter(
    val id: String,
    val label: String,
    val monitored: Boolean? = null,
    val missing: Boolean? = null,
    val quality: Set<String> = emptySet(),
    val metadata: Set<String> = emptySet(),
    val genre: Set<String> = emptySet(),
    val folder: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val albumsMin: Double? = null,
    val albumsMax: Double? = null,
)

/** Converts a saved filter into the framework state the presenter consumes. */
fun SavedArtistFilter.toFilterState(): FilterState {
    var state = FilterState()
    monitored?.let { state = state.with(ArtistLibraryFilters.MONITORED, FilterValue.Toggle(it)) }
    missing?.let { state = state.with(ArtistLibraryFilters.MISSING, FilterValue.Toggle(it)) }
    if (quality.isNotEmpty()) {
        state = state.with(ArtistLibraryFilters.QUALITY, FilterValue.Choice(quality))
    }
    if (metadata.isNotEmpty()) {
        state = state.with(ArtistLibraryFilters.METADATA, FilterValue.Choice(metadata))
    }
    if (genre.isNotEmpty()) {
        state = state.with(ArtistLibraryFilters.GENRE, FilterValue.Choice(genre))
    }
    if (folder.isNotEmpty()) {
        state = state.with(ArtistLibraryFilters.FOLDER, FilterValue.Choice(folder))
    }
    if (tags.isNotEmpty()) {
        state = state.with(ArtistLibraryFilters.TAGS, FilterValue.Choice(tags))
    }
    if (albumsMin != null || albumsMax != null) {
        state = state.with(ArtistLibraryFilters.ALBUMS, FilterValue.Range(albumsMin, albumsMax))
    }
    return state
}

/** Converts live filter state into the serializable saved form. */
fun FilterState.toSavedValues(): SavedArtistFilterValues {
    fun choice(id: String): Set<String> = (values[id] as? FilterValue.Choice)?.selected ?: emptySet()
    val range = values[ArtistLibraryFilters.ALBUMS] as? FilterValue.Range
    return SavedArtistFilterValues(
        monitored = (values[ArtistLibraryFilters.MONITORED] as? FilterValue.Toggle)?.on,
        missing = (values[ArtistLibraryFilters.MISSING] as? FilterValue.Toggle)?.on,
        quality = choice(ArtistLibraryFilters.QUALITY),
        metadata = choice(ArtistLibraryFilters.METADATA),
        genre = choice(ArtistLibraryFilters.GENRE),
        folder = choice(ArtistLibraryFilters.FOLDER),
        tags = choice(ArtistLibraryFilters.TAGS),
        albumsMin = range?.min,
        albumsMax = range?.max,
    )
}

/** Value half of [toSavedValues], kept separate so ids stay internal. */
@Serializable
data class SavedArtistFilterValues(
    val monitored: Boolean? = null,
    val missing: Boolean? = null,
    val quality: Set<String> = emptySet(),
    val metadata: Set<String> = emptySet(),
    val genre: Set<String> = emptySet(),
    val folder: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val albumsMin: Double? = null,
    val albumsMax: Double? = null,
)

/** Builds a saved filter from a label and live state, assigning a fresh id. */
fun newSavedFilter(label: String, state: FilterState): SavedArtistFilter {
    val values = state.toSavedValues()
    return SavedArtistFilter(
        id = UUID.randomUUID().toString(),
        label = label,
        monitored = values.monitored,
        missing = values.missing,
        quality = values.quality,
        metadata = values.metadata,
        genre = values.genre,
        folder = values.folder,
        tags = values.tags,
        albumsMin = values.albumsMin,
        albumsMax = values.albumsMax,
    )
}

/**
 * Saved custom filters persisted through the committed [EntityStore], one row
 * per filter, scoped to the server whose library they filter. Adds no storage
 * mechanism of its own.
 */
class SavedArtistFiltersStore(
    private val store: EntityStore,
    private val serverId: ServerId,
) {
    fun observe(): Flow<List<SavedArtistFilter>> =
        store.observe(serverId, TYPE, SavedArtistFilter.serializer())
            .map { rows -> rows.sortedBy { it.label.lowercase() } }
            .distinctUntilChanged()

    suspend fun save(filter: SavedArtistFilter) {
        store.put(serverId, TYPE, filter.id, filter, SavedArtistFilter.serializer())
    }

    suspend fun delete(id: String) {
        store.delete(serverId, TYPE, id)
    }

    private companion object {
        val TYPE = EntityType("lidarr.savedfilter")
    }
}
