package dev.appolon.homelab.arr.feature.lidarr.library.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.lidarr.model.ApplyTags
import dev.appolon.homelab.arr.api.lidarr.model.ArtistEditorResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.MetadataProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.NewItemMonitorTypes
import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.FilterState
import dev.appolon.homelab.arr.core.FilterValue
import dev.appolon.homelab.arr.feature.lidarr.library.data.ArtistAlbumSummary
import dev.appolon.homelab.arr.feature.lidarr.library.data.ArtistCommand
import dev.appolon.homelab.arr.feature.lidarr.library.data.ArtistLibraryFilters
import dev.appolon.homelab.arr.feature.lidarr.library.data.ArtistLibrarySorts
import dev.appolon.homelab.arr.feature.lidarr.library.data.ArtistPresentation
import dev.appolon.homelab.arr.feature.lidarr.library.data.LidarrArtistsRepository
import dev.appolon.homelab.arr.feature.lidarr.library.data.SavedArtistFilter
import dev.appolon.homelab.arr.feature.lidarr.library.data.SavedArtistFiltersStore
import dev.appolon.homelab.arr.feature.lidarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.lidarr.library.data.artistAlbumSummaries
import dev.appolon.homelab.arr.feature.lidarr.library.data.newSavedFilter
import dev.appolon.homelab.arr.feature.lidarr.library.data.parseTagIds
import dev.appolon.homelab.arr.feature.lidarr.library.data.presentArtists
import dev.appolon.homelab.arr.feature.lidarr.library.data.toFilterState
import dev.appolon.homelab.arr.net.arrError
import dev.appolon.homelab.arr.ui.lce.LceState
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which layout the library renders. */
enum class LibraryView { POSTERS, TABLE }

/**
 * The four monitoring strategies the editor sheet offers. `missing` is the
 * album-studio strategy; the other three map onto the editor endpoint's
 * monitor-new-items field.
 */
enum class MonitorStrategy { ALL, NONE, NEW, MISSING }

/** Transient one-line result of a write, surfaced as a snackbar. */
sealed interface LibraryMessage {
    data object EditSynced : LibraryMessage
    data object EditQueued : LibraryMessage
    data object DeleteSynced : LibraryMessage
    data object DeleteQueued : LibraryMessage
    data object CommandStarted : LibraryMessage
    data object CommandQueued : LibraryMessage
    data class WriteFailed(val error: ArrError) : LibraryMessage
}

/** Form state of the bulk artist editor sheet; null fields mean leave unchanged. */
data class ArtistEditorForm(
    val monitored: Boolean? = null,
    val monitorStrategy: MonitorStrategy? = null,
    val qualityProfileId: Int? = null,
    val metadataProfileId: Int? = null,
    val rootFolderPath: String = "",
    val tagsText: String = "",
    val applyTags: ApplyTags = ApplyTags.ADD,
    val moveFiles: Boolean = false,
    val tagsError: Boolean = false,
)

/**
 * State of the artists library screen. Everything the composables render is
 * here; presentation (filter, sort, window) derives through
 * [ArtistLibraryFilters] helpers so it stays testable.
 */
data class ArtistLibraryUiState(
    val loaded: Boolean = false,
    val loadError: ArrError? = null,
    val artists: List<ArtistResource> = emptyList(),
    val summaries: Map<Int, ArtistAlbumSummary> = emptyMap(),
    val qualityProfiles: List<QualityProfileResource> = emptyList(),
    val metadataProfiles: List<MetadataProfileResource> = emptyList(),
    val filterState: FilterState = FilterState(),
    val savedFilters: List<SavedArtistFilter> = emptyList(),
    val activeSavedFilterId: String? = null,
    val sortId: String = ArtistLibrarySorts.TITLE,
    val sortDescending: Boolean = false,
    val view: LibraryView = LibraryView.POSTERS,
    val visibleCount: Int = ArtistPresentation.INITIAL_COUNT,
    val selectMode: Boolean = false,
    val selection: Set<Int> = emptySet(),
    val queuedForServer: Int = 0,
    val queuedArtistIds: Set<Int> = emptySet(),
    val refreshing: Boolean = false,
    val editorForm: ArtistEditorForm? = null,
    val confirmDelete: Boolean = false,
    val message: LibraryMessage? = null,
) {

    /** Filter options derived from the artists in view. */
    val options: ArtistLibraryFilters.Options by lazy(LazyThreadSafetyMode.NONE) {
        ArtistLibraryFilters.optionsFrom(artists, summaries)
    }

    /** The filtered, sorted, windowed presentation the lazy lists render. */
    val presentation: ArtistPresentation by lazy(LazyThreadSafetyMode.NONE) {
        presentArtists(artists, options, summaries, filterState, sortId, sortDescending, visibleCount)
    }

    /** Label of the quality profile, falling back to the raw id. */
    fun qualityProfileLabel(id: Int): String =
        qualityProfiles.firstOrNull { it.id == id }?.name ?: "#$id"

    /** Label of the metadata profile, falling back to the raw id. */
    fun metadataProfileLabel(id: Int): String =
        metadataProfiles.firstOrNull { it.id == id }?.name ?: "#$id"

    /** ids the next bulk operation targets: the selection, or everything presented. */
    val bulkTargets: List<Int>
        get() = if (selectMode && selection.isNotEmpty()) selection.sorted() else presentation.all.map { it.id }
}

/**
 * ViewModel of the Lidarr artists library. Owns no Android types so unit tests
 * construct it directly against fakes; the destination composable wires it to
 * the [dev.appolon.homelab.arr.data.ArrDataGraph] singletons.
 */
class ArtistLibraryViewModel(
    private val repository: LidarrArtistsRepository,
    private val savedFilters: SavedArtistFiltersStore,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {

    private val _state = MutableStateFlow(ArtistLibraryUiState())
    val state: StateFlow<ArtistLibraryUiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<ArtistLibraryUiState>> = state
        .map { s ->
            when {
                s.loadError != null && s.artists.isEmpty() -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.artists.isEmpty() -> LceState.Empty
                else -> LceState.Content(s, refreshing = s.refreshing)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeArtists()
                .catch { t -> _state.update { it.copy(loaded = true, loadError = t.arrError()) } }
                .collect { artists -> _state.update { it.copy(artists = artists, loaded = true) } }
        }
        viewModelScope.launch {
            // Album failures only degrade the release-status counts, never the list.
            repository.observeAlbums()
                .catch { }
                .collect { albums ->
                    val known = _state.value.artists.map { it.id }.toSet()
                    _state.update { s ->
                        s.copy(
                            summaries = artistAlbumSummaries(
                                albums.filter { it.artistId in known },
                                now(),
                            ),
                        )
                    }
                }
        }
        viewModelScope.launch {
            savedFilters.observe()
                .catch { }
                .collect { filters ->
                    _state.update { current ->
                        val stillThere = current.activeSavedFilterId
                            ?.let { id -> filters.any { it.id == id } } == true
                        current.copy(
                            savedFilters = filters,
                            activeSavedFilterId = current.activeSavedFilterId.takeIf { stillThere },
                        )
                    }
                }
        }
        viewModelScope.launch {
            repository.queuedCount()
                .catch { }
                .collect { count -> _state.update { it.copy(queuedForServer = count) } }
        }
        viewModelScope.launch {
            repository.queuedArtistIds()
                .catch { }
                .collect { ids -> _state.update { it.copy(queuedArtistIds = ids) } }
        }
        viewModelScope.launch { refreshProfiles() }
        refresh()
    }

    /** Pull-to-refresh and error retry entry point. */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            when (val result = repository.refresh()) {
                is ArrResult.Ok -> _state.update { it.copy(refreshing = false, loadError = null) }
                is ArrResult.Err -> _state.update { it.copy(refreshing = false, loadError = result.error) }
            }
        }
    }

    fun setView(view: LibraryView) = _state.update { it.copy(view = view) }

    fun setSort(sortId: String) =
        _state.update { it.copy(sortId = sortId, visibleCount = ArtistPresentation.INITIAL_COUNT) }

    fun toggleSortDescending() = _state.update { it.copy(sortDescending = !it.sortDescending) }

    /** Grows the visible window by one page; a no-op at the end of the list. */
    fun loadMore() = _state.update { s ->
        if (s.presentation.endReached) {
            s
        } else {
            s.copy(visibleCount = s.visibleCount + ArtistPresentation.PAGE_COUNT)
        }
    }

    fun setFilterValue(id: String, value: FilterValue) = _state.update { s ->
        val next = if (value == emptyValue(id)) s.filterState.without(id) else s.filterState.with(id, value)
        s.copy(
            filterState = next,
            activeSavedFilterId = null,
            visibleCount = ArtistPresentation.INITIAL_COUNT,
        )
    }

    fun clearFilters() = _state.update { s ->
        s.copy(filterState = FilterState(), activeSavedFilterId = null)
    }

    /** Saves the current filter state under a label and activates it. */
    fun saveFilter(label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        val filter = newSavedFilter(trimmed, _state.value.filterState)
        viewModelScope.launch {
            savedFilters.save(filter)
            _state.update { it.copy(activeSavedFilterId = filter.id) }
        }
    }

    fun applySavedFilter(id: String) {
        val filter = _state.value.savedFilters.firstOrNull { it.id == id } ?: return
        _state.update { s ->
            if (s.activeSavedFilterId == id) {
                s.copy(filterState = FilterState(), activeSavedFilterId = null)
            } else {
                s.copy(
                    filterState = filter.toFilterState(),
                    activeSavedFilterId = id,
                    visibleCount = ArtistPresentation.INITIAL_COUNT,
                )
            }
        }
    }

    fun deleteSavedFilter(id: String) {
        viewModelScope.launch { savedFilters.delete(id) }
    }

    fun enterSelectMode(first: Int) =
        _state.update { it.copy(selectMode = true, selection = setOf(first)) }

    fun exitSelectMode() = _state.update { it.copy(selectMode = false, selection = emptySet()) }

    fun toggleSelected(artistId: Int) = _state.update { s ->
        val next = if (artistId in s.selection) s.selection - artistId else s.selection + artistId
        s.copy(selection = next, selectMode = next.isNotEmpty() || s.selectMode)
    }

    fun selectAllPresented() = _state.update { s ->
        s.copy(selection = s.presentation.all.map { it.id }.toSet(), selectMode = true)
    }

    fun openEditor() = _state.update { it.copy(editorForm = ArtistEditorForm()) }

    fun closeEditor() = _state.update { it.copy(editorForm = null) }

    fun updateEditor(transform: (ArtistEditorForm) -> ArtistEditorForm) =
        _state.update { s ->
            val form = s.editorForm ?: return@update s
            s.copy(editorForm = transform(form))
        }

    /** Applies the editor form to the current bulk targets. */
    fun submitEditor() {
        val form = _state.value.editorForm ?: return
        val tags = parseTagIds(form.tagsText)
        if (tags == null) {
            _state.update { it.copy(editorForm = form.copy(tagsError = true)) }
            return
        }
        val targets = _state.value.bulkTargets
        val editor = ArtistEditorResource(
            artistIds = targets,
            monitored = form.monitored,
            monitorNewItems = resolveMonitorNewItems(form.monitorStrategy, targets),
            qualityProfileId = form.qualityProfileId,
            metadataProfileId = form.metadataProfileId,
            rootFolderPath = form.rootFolderPath.trim().ifEmpty { null },
            // An empty tag list only travels when REPLACE means "clear tags";
            // ADD and REMOVE with nothing selected leave tags untouched.
            tags = tags.takeIf { it.isNotEmpty() || form.applyTags == ApplyTags.REPLACE },
            applyTags = form.applyTags,
            moveFiles = form.moveFiles,
        )
        viewModelScope.launch {
            when (val outcome = repository.editArtists(editor, form.monitorStrategy == MonitorStrategy.MISSING)) {
                is ArrResult.Ok -> _state.update { s ->
                    s.copy(
                        editorForm = null,
                        message = if (outcome.value is WriteOutcome.Queued) {
                            LibraryMessage.EditQueued
                        } else {
                            LibraryMessage.EditSynced
                        },
                    )
                }
                is ArrResult.Err -> _state.update { s ->
                    s.copy(message = LibraryMessage.WriteFailed(outcome.error))
                }
            }
        }
    }

    /**
     * The editor payload always carries monitor-new-items, so "unchanged"
     * resolves to the strategy the targets already share, or ALL when they
     * disagree, mirroring what the server would keep for an untouched field.
     */
    private fun resolveMonitorNewItems(strategy: MonitorStrategy?, targets: List<Int>): NewItemMonitorTypes {
        strategy?.let { return it.toNewItemMonitorTypes() }
        val current = _state.value.artists.filter { it.id in targets }.map { it.monitorNewItems }.distinct()
        return current.singleOrNull() ?: NewItemMonitorTypes.ALL
    }

    private fun MonitorStrategy.toNewItemMonitorTypes(): NewItemMonitorTypes = when (this) {
        MonitorStrategy.ALL -> NewItemMonitorTypes.ALL
        MonitorStrategy.NONE, MonitorStrategy.MISSING -> NewItemMonitorTypes.NONE
        MonitorStrategy.NEW -> NewItemMonitorTypes.NEW
    }

    fun requestDelete() = _state.update { it.copy(confirmDelete = true) }

    fun dismissDelete() = _state.update { it.copy(confirmDelete = false) }

    /** Bulk-deletes the current targets with the chosen flags. */
    fun confirmDelete(deleteFiles: Boolean, addImportExclusion: Boolean) {
        val targets = _state.value.bulkTargets
        _state.update { it.copy(confirmDelete = false) }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            when (val outcome = repository.deleteArtists(targets, deleteFiles, addImportExclusion)) {
                is ArrResult.Ok -> _state.update { s ->
                    s.copy(
                        selectMode = false,
                        selection = emptySet(),
                        message = if (outcome.value is WriteOutcome.Queued) {
                            LibraryMessage.DeleteQueued
                        } else {
                            LibraryMessage.DeleteSynced
                        },
                    )
                }
                is ArrResult.Err -> _state.update { s ->
                    s.copy(message = LibraryMessage.WriteFailed(outcome.error))
                }
            }
        }
    }

    /** Refresh metadata for the selection, or the whole library when nothing is selected. */
    fun refreshMetadata() = dispatchCommand(ArtistCommand.REFRESH_METADATA)

    /** Rescan disk for the selection, or the whole library when nothing is selected. */
    fun scanDisk() = dispatchCommand(ArtistCommand.SCAN_DISK)

    private fun dispatchCommand(command: ArtistCommand) {
        val scoped = _state.value.takeIf { it.selectMode && it.selection.isNotEmpty() }
            ?.selection?.sorted()
        viewModelScope.launch {
            when (val outcome = repository.runCommand(command, scoped)) {
                is ArrResult.Ok -> _state.update { s ->
                    s.copy(
                        message = if (outcome.value is WriteOutcome.Queued) {
                            LibraryMessage.CommandQueued
                        } else {
                            LibraryMessage.CommandStarted
                        },
                    )
                }
                is ArrResult.Err -> _state.update { s ->
                    s.copy(message = LibraryMessage.WriteFailed(outcome.error))
                }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private suspend fun refreshProfiles() {
        when (val quality = repository.qualityProfiles()) {
            is ArrResult.Ok -> _state.update { it.copy(qualityProfiles = quality.value) }
            is ArrResult.Err -> Unit
        }
        when (val metadata = repository.metadataProfiles()) {
            is ArrResult.Ok -> _state.update { it.copy(metadataProfiles = metadata.value) }
            is ArrResult.Err -> Unit
        }
    }

    private fun emptyValue(id: String): FilterValue = when (id) {
        ArtistLibraryFilters.ALBUMS -> FilterValue.Range(null, null)
        else -> FilterValue.Choice(emptySet())
    }
}
