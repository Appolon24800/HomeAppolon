package dev.appolon.homelab.arr.feature.sonarr.library.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.sonarr.model.ApplyTags
import dev.appolon.homelab.arr.api.sonarr.model.MonitoringOptions
import dev.appolon.homelab.arr.api.sonarr.model.MonitorTypes
import dev.appolon.homelab.arr.api.sonarr.model.SeasonPassResource
import dev.appolon.homelab.arr.api.sonarr.model.SeasonPassSeriesResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesEditorResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesTypes
import dev.appolon.homelab.arr.api.sonarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.FilterState
import dev.appolon.homelab.arr.core.FilterValue
import dev.appolon.homelab.arr.feature.sonarr.library.data.SavedSeriesFilter
import dev.appolon.homelab.arr.feature.sonarr.library.data.SavedSeriesFiltersStore
import dev.appolon.homelab.arr.feature.sonarr.library.data.SeriesCommand
import dev.appolon.homelab.arr.feature.sonarr.library.data.SeriesLibraryFilters
import dev.appolon.homelab.arr.feature.sonarr.library.data.SeriesLibrarySorts
import dev.appolon.homelab.arr.feature.sonarr.library.data.SeriesPresentation
import dev.appolon.homelab.arr.feature.sonarr.library.data.SonarrSeriesRepository
import dev.appolon.homelab.arr.feature.sonarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.sonarr.library.data.newSavedFilter
import dev.appolon.homelab.arr.feature.sonarr.library.data.parseTagIds
import dev.appolon.homelab.arr.feature.sonarr.library.data.presentSeries
import dev.appolon.homelab.arr.feature.sonarr.library.data.toFilterState
import dev.appolon.homelab.arr.net.arrError
import dev.appolon.homelab.arr.ui.lce.LceState
import java.time.Year
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

/** Transient one-line result of a write, surfaced as a snackbar. */
sealed interface LibraryMessage {
    data object EditSynced : LibraryMessage
    data object EditQueued : LibraryMessage
    data object SeasonPassSynced : LibraryMessage
    data object SeasonPassQueued : LibraryMessage
    data object DeleteSynced : LibraryMessage
    data object DeleteQueued : LibraryMessage
    data object CommandStarted : LibraryMessage
    data object CommandQueued : LibraryMessage
    data class WriteFailed(val error: ArrError) : LibraryMessage
}

/** Form state of the bulk series editor sheet; null fields mean leave unchanged. */
data class SeriesEditorForm(
    val monitored: Boolean? = null,
    val qualityProfileId: Int? = null,
    val seriesType: SeriesTypes? = null,
    val seasonFolder: Boolean? = null,
    val rootFolderPath: String = "",
    val tagsText: String = "",
    val applyTags: ApplyTags = ApplyTags.ADD,
    val moveFiles: Boolean = false,
    val tagsError: Boolean = false,
)

/** Form state of the season pass sheet. */
data class SeasonPassForm(
    val monitored: Boolean = true,
    val monitor: MonitorTypes = MonitorTypes.ALL,
    val ignoreEpisodesWithFiles: Boolean = false,
    val ignoreEpisodesWithoutFiles: Boolean = false,
)

/**
 * State of the series library screen. Everything the composables render is
 * here; presentation (filter, sort, window) derives through
 * [SeriesLibraryFilters] helpers so it stays testable.
 */
data class SeriesLibraryUiState(
    val loaded: Boolean = false,
    val loadError: ArrError? = null,
    val series: List<SeriesResource> = emptyList(),
    val qualityProfiles: List<QualityProfileResource> = emptyList(),
    val tags: List<TagResource> = emptyList(),
    val filterState: FilterState = FilterState(),
    val savedFilters: List<SavedSeriesFilter> = emptyList(),
    val activeSavedFilterId: String? = null,
    val sortId: String = SeriesLibrarySorts.TITLE,
    val sortDescending: Boolean = false,
    val view: LibraryView = LibraryView.POSTERS,
    val visibleCount: Int = SeriesPresentation.INITIAL_COUNT,
    val selectMode: Boolean = false,
    val selection: Set<Int> = emptySet(),
    val queuedForServer: Int = 0,
    val queuedSeriesIds: Set<Int> = emptySet(),
    val refreshing: Boolean = false,
    val editorForm: SeriesEditorForm? = null,
    val seasonPassForm: SeasonPassForm? = null,
    val confirmDelete: Boolean = false,
    val message: LibraryMessage? = null,
) {

    /** Filter options derived from the series in view. */
    val options: SeriesLibraryFilters.Options by lazy(LazyThreadSafetyMode.NONE) {
        SeriesLibraryFilters.optionsFrom(series, Year.now().value)
    }

    /** The filtered, sorted, windowed presentation the lazy lists render. */
    val presentation: SeriesPresentation by lazy(LazyThreadSafetyMode.NONE) {
        presentSeries(series, options, filterState, sortId, sortDescending, visibleCount)
    }

    /** Label of the quality profile, falling back to the raw id. */
    fun profileLabel(id: Int): String =
        qualityProfiles.firstOrNull { it.id == id }?.name ?: "#$id"

    /** Label of a tag, falling back to the raw id. */
    fun tagLabel(id: Int): String =
        tags.firstOrNull { it.id == id }?.label ?: "#$id"

    /** ids the next bulk operation targets: the selection, or everything presented. */
    val bulkTargets: List<Int>
        get() = if (selectMode && selection.isNotEmpty()) {
            selection.sorted()
        } else {
            presentation.all.map { it.id }
        }
}

/**
 * ViewModel of the Sonarr series library. Owns no Android types so unit tests
 * construct it directly against fakes; the destination composable wires it to
 * the [dev.appolon.homelab.arr.data.ArrDataGraph] singletons.
 */
class SeriesLibraryViewModel(
    private val repository: SonarrSeriesRepository,
    private val savedFilters: SavedSeriesFiltersStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SeriesLibraryUiState())
    val state: StateFlow<SeriesLibraryUiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<SeriesLibraryUiState>> = state
        .map { s ->
            when {
                s.loadError != null && s.series.isEmpty() -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.series.isEmpty() -> LceState.Empty
                else -> LceState.Content(s, refreshing = s.refreshing)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeSeries()
                .catch { t -> _state.update { it.copy(loaded = true, loadError = t.arrError()) } }
                .collect { series -> _state.update { it.copy(series = series, loaded = true) } }
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
            repository.queuedSeriesIds()
                .catch { }
                .collect { ids -> _state.update { it.copy(queuedSeriesIds = ids) } }
        }
        viewModelScope.launch { refreshLookups() }
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
        _state.update { it.copy(sortId = sortId, visibleCount = SeriesPresentation.INITIAL_COUNT) }

    fun toggleSortDescending() = _state.update { it.copy(sortDescending = !it.sortDescending) }

    /** Grows the visible window by one page; a no-op at the end of the list. */
    fun loadMore() = _state.update { s ->
        if (s.presentation.endReached) {
            s
        } else {
            s.copy(visibleCount = s.visibleCount + SeriesPresentation.PAGE_COUNT)
        }
    }

    fun setFilterValue(id: String, value: FilterValue) = _state.update { s ->
        val next = if (value == emptyValue(id)) s.filterState.without(id) else s.filterState.with(id, value)
        s.copy(
            filterState = next,
            activeSavedFilterId = null,
            visibleCount = SeriesPresentation.INITIAL_COUNT,
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
                    visibleCount = SeriesPresentation.INITIAL_COUNT,
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

    fun toggleSelected(seriesId: Int) = _state.update { s ->
        val next = if (seriesId in s.selection) s.selection - seriesId else s.selection + seriesId
        s.copy(selection = next, selectMode = next.isNotEmpty() || s.selectMode)
    }

    fun selectAllPresented() = _state.update { s ->
        s.copy(selection = s.presentation.all.map { it.id }.toSet(), selectMode = true)
    }

    fun openEditor() = _state.update { it.copy(editorForm = SeriesEditorForm()) }

    fun closeEditor() = _state.update { it.copy(editorForm = null) }

    fun updateEditor(transform: (SeriesEditorForm) -> SeriesEditorForm) =
        _state.update { s ->
            val form = s.editorForm ?: return@update s
            s.copy(editorForm = transform(form))
        }

    /**
     * Applies the editor form to the current bulk targets. `seriesType` has
     * no null wire form, so an explicit choice travels; a null choice reuses
     * the selection's uniform type or the first series' type, keeping mixed
     * selections from silently flipping to standard.
     */
    fun submitEditor() {
        val form = _state.value.editorForm ?: return
        val tags = parseTagIds(form.tagsText)
        if (tags == null) {
            _state.update { it.copy(editorForm = form.copy(tagsError = true)) }
            return
        }
        val state = _state.value
        val targets = state.bulkTargets
        val fallbackType = state.series
            .filter { it.id in targets.toSet() }
            .map { it.seriesType }
            .distinct()
            .singleOrNull() ?: SeriesTypes.STANDARD
        val sendsTags = tags.isNotEmpty() || form.applyTags == ApplyTags.REPLACE
        val editor = SeriesEditorResource(
            seriesIds = targets,
            monitored = form.monitored,
            qualityProfileId = form.qualityProfileId,
            seriesType = form.seriesType ?: fallbackType,
            seasonFolder = form.seasonFolder,
            rootFolderPath = form.rootFolderPath.trim().ifEmpty { null },
            tags = tags.takeIf { sendsTags },
            applyTags = if (sendsTags) form.applyTags else ApplyTags.ADD,
            moveFiles = form.moveFiles,
        )
        viewModelScope.launch {
            when (val outcome = repository.editSeries(editor)) {
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

    fun openSeasonPass() =
        _state.update { it.copy(seasonPassForm = SeasonPassForm()) }

    fun closeSeasonPass() = _state.update { it.copy(seasonPassForm = null) }

    fun updateSeasonPass(transform: (SeasonPassForm) -> SeasonPassForm) =
        _state.update { s ->
            val form = s.seasonPassForm ?: return@update s
            s.copy(seasonPassForm = transform(form))
        }

    /**
     * Applies the season pass form to the current bulk targets: series-level
     * monitoring plus per-season flags mirroring the toggle, with the monitor
     * strategy traveling for the server to recompute episode state.
     */
    fun submitSeasonPass() {
        val form = _state.value.seasonPassForm ?: return
        val state = _state.value
        val targets = state.bulkTargets.toSet()
        val entries = state.series
            .filter { it.id in targets }
            .map { series ->
                SeasonPassSeriesResource(
                    id = series.id,
                    monitored = form.monitored,
                    seasons = series.seasons.orEmpty().map { season ->
                        season.copy(monitored = form.monitored)
                    },
                )
            }
        if (entries.isEmpty()) return
        val pass = SeasonPassResource(
            series = entries,
            monitoringOptions = MonitoringOptions(
                ignoreEpisodesWithFiles = form.ignoreEpisodesWithFiles,
                ignoreEpisodesWithoutFiles = form.ignoreEpisodesWithoutFiles,
                monitor = form.monitor,
            ),
        )
        viewModelScope.launch {
            when (val outcome = repository.seasonPass(pass)) {
                is ArrResult.Ok -> _state.update { s ->
                    s.copy(
                        seasonPassForm = null,
                        message = if (outcome.value is WriteOutcome.Queued) {
                            LibraryMessage.SeasonPassQueued
                        } else {
                            LibraryMessage.SeasonPassSynced
                        },
                    )
                }

                is ArrResult.Err -> _state.update { s ->
                    s.copy(message = LibraryMessage.WriteFailed(outcome.error))
                }
            }
        }
    }

    fun requestDelete() = _state.update { it.copy(confirmDelete = true) }

    fun dismissDelete() = _state.update { it.copy(confirmDelete = false) }

    /** Bulk-deletes the current targets with the chosen flags. */
    fun confirmDelete(deleteFiles: Boolean, addImportListExclusion: Boolean) {
        val targets = _state.value.bulkTargets
        _state.update { it.copy(confirmDelete = false) }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            when (val outcome = repository.deleteSeries(targets, deleteFiles, addImportListExclusion)) {
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
    fun refreshMetadata() = dispatchCommand(SeriesCommand.REFRESH_METADATA)

    /** Rescan disk for the selection, or the whole library when nothing is selected. */
    fun scanDisk() = dispatchCommand(SeriesCommand.SCAN_DISK)

    private fun dispatchCommand(command: SeriesCommand) {
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

    private suspend fun refreshLookups() {
        when (val profiles = repository.qualityProfiles()) {
            is ArrResult.Ok -> _state.update { it.copy(qualityProfiles = profiles.value) }
            is ArrResult.Err -> Unit
        }
        when (val tags = repository.tags()) {
            is ArrResult.Ok -> _state.update { it.copy(tags = tags.value) }
            is ArrResult.Err -> Unit
        }
    }

    private fun emptyValue(id: String): FilterValue = when (id) {
        SeriesLibraryFilters.YEAR, SeriesLibraryFilters.SEASONS -> FilterValue.Range(null, null)
        SeriesLibraryFilters.MONITORED, SeriesLibraryFilters.MISSING -> FilterValue.Toggle(null)
        else -> FilterValue.Choice(emptySet())
    }
}
