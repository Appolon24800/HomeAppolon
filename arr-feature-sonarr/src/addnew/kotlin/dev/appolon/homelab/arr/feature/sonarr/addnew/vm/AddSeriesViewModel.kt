package dev.appolon.homelab.arr.feature.sonarr.addnew.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.sonarr.model.AddSeriesOptions
import dev.appolon.homelab.arr.api.sonarr.model.ImportListExclusionResource
import dev.appolon.homelab.arr.api.sonarr.model.ImportListResource
import dev.appolon.homelab.arr.api.sonarr.model.LanguageProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.MonitorTypes
import dev.appolon.homelab.arr.api.sonarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.RootFolderResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesTypes
import dev.appolon.homelab.arr.api.sonarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.sonarr.addnew.data.SeasonMonitoring
import dev.appolon.homelab.arr.feature.sonarr.addnew.data.SonarrAddRepository
import dev.appolon.homelab.arr.feature.sonarr.library.data.WriteOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which tab the add-new screen shows. */
enum class AddTab { SEARCH, LISTS }

/** Transient one-line result of a write, surfaced as a snackbar. */
sealed interface AddMessage {
    data class Added(val title: String) : AddMessage
    data class AddQueued(val title: String) : AddMessage
    data object AddFailed : AddMessage
    data object ListSynced : AddMessage
    data object ListSyncQueued : AddMessage
    data object ListSyncFailed : AddMessage
    data object ExclusionSaved : AddMessage
    data object ExclusionDeleted : AddMessage
    data object ExclusionQueued : AddMessage
    data object ExclusionFailed : AddMessage
}

/** Form state of the add-series sheet for one lookup result. */
data class AddSeriesForm(
    val series: SeriesResource,
    val monitored: Boolean = true,
    val rootFolderPath: String = "",
    val qualityProfileId: Int = 0,
    val languageProfileId: Int = 0,
    val seriesType: SeriesTypes = SeriesTypes.STANDARD,
    val monitor: MonitorTypes = MonitorTypes.ALL,
    val monitoredSeasons: Set<Int> = emptySet(),
    val seasonFolder: Boolean = true,
    val tagIds: Set<Int> = emptySet(),
)

/** Draft of a new import list exclusion inside the add-exclusion dialog. */
data class ExclusionDraft(
    val title: String = "",
    val tvdbText: String = "",
    val titleError: Boolean = false,
    val tvdbError: Boolean = false,
)

/**
 * State of the add-new screen: search with year disambiguation, the add
 * sheet, and the import-lists tab. The sheet's defaults come from whatever
 * lookups have loaded; the add button stays inert until a root folder path
 * exists.
 */
data class AddSeriesUiState(
    val query: String = "",
    val searching: Boolean = false,
    val searched: Boolean = false,
    val searchError: ArrError? = null,
    val results: List<SeriesResource> = emptyList(),
    val yearFilter: Int? = null,
    val libraryTvdbIds: Set<Int> = emptySet(),
    val queuedTvdbIds: Set<Int> = emptySet(),
    val queuedForServer: Int = 0,
    val rootFolders: List<RootFolderResource> = emptyList(),
    val qualityProfiles: List<QualityProfileResource> = emptyList(),
    val languageProfiles: List<LanguageProfileResource> = emptyList(),
    val tags: List<TagResource> = emptyList(),
    val listsLoading: Boolean = false,
    val listsError: ArrError? = null,
    val importLists: List<ImportListResource> = emptyList(),
    val exclusions: List<ImportListExclusionResource> = emptyList(),
    val form: AddSeriesForm? = null,
    val submitting: Boolean = false,
    val exclusionDraft: ExclusionDraft? = null,
    val message: AddMessage? = null,
) {
    /** Distinct years across the current results, for the disambiguation chips. */
    val availableYears: List<Int>
        get() = results.mapNotNull { it.year.takeIf { year -> year > 0 } }.distinct().sorted()

    /** Results after the optional year filter. */
    val visibleResults: List<SeriesResource>
        get() = results.filter { yearFilter == null || it.year == yearFilter }
}

/**
 * ViewModel of the Sonarr add-new screen. Owns no Android types so unit
 * tests construct it directly against fakes; the destination composable
 * wires it to the [dev.appolon.homelab.arr.data.ArrDataGraph] singletons.
 */
class AddSeriesViewModel(
    private val repository: SonarrAddRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AddSeriesUiState())
    val state: StateFlow<AddSeriesUiState> = _state.asStateFlow()

    private var debounceJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeSeries()
                .catch { }
                .collect { series ->
                    _state.update {
                        it.copy(
                            libraryTvdbIds = series
                                .mapNotNull { s -> s.tvdbId.takeIf { id -> id > 0 } }
                                .toSet(),
                        )
                    }
                }
        }
        viewModelScope.launch {
            repository.queuedTvdbIds()
                .catch { }
                .collect { ids -> _state.update { it.copy(queuedTvdbIds = ids) } }
        }
        viewModelScope.launch {
            repository.queuedCount()
                .catch { }
                .collect { count -> _state.update { it.copy(queuedForServer = count) } }
        }
        viewModelScope.launch { repository.refreshSeriesIfStale() }
        refreshDefaults()
        refreshLists()
    }

    /** Updates the query and schedules a debounced search; typing settles it. */
    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
        debounceJob?.cancel()
        if (value.isBlank()) {
            _state.update {
                it.copy(
                    searching = false,
                    results = emptyList(),
                    searched = false,
                    searchError = null,
                )
            }
            return
        }
        debounceJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            search()
        }
    }

    /** Immediate search; used by the debounce, keyboard action, and retry. */
    fun search() {
        val term = _state.value.query.trim()
        if (term.isEmpty()) return
        debounceJob?.cancel()
        viewModelScope.launch {
            _state.update { it.copy(searching = true, searchError = null) }
            when (val result = repository.lookup(term)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        searching = false,
                        searched = true,
                        results = result.value,
                        yearFilter = null,
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(
                        searching = false,
                        searched = true,
                        searchError = result.error,
                        results = emptyList(),
                        yearFilter = null,
                    )
                }
            }
        }
    }

    fun setYearFilter(year: Int?) = _state.update { it.copy(yearFilter = year) }

    /** Loads sheet defaults (folders, profiles, tags) cache-first. */
    fun refreshDefaults() {
        viewModelScope.launch {
            when (val folders = repository.rootFolders()) {
                is ArrResult.Ok -> _state.update { it.copy(rootFolders = folders.value) }
                is ArrResult.Err -> Unit
            }
            when (val profiles = repository.qualityProfiles()) {
                is ArrResult.Ok -> _state.update { it.copy(qualityProfiles = profiles.value) }
                is ArrResult.Err -> Unit
            }
            when (val languages = repository.languageProfiles()) {
                is ArrResult.Ok -> _state.update { it.copy(languageProfiles = languages.value) }
                is ArrResult.Err -> Unit
            }
            when (val tags = repository.tags()) {
                is ArrResult.Ok -> _state.update { it.copy(tags = tags.value) }
                is ArrResult.Err -> Unit
            }
        }
    }

    /** Reloads import lists and exclusions for the lists tab. */
    fun refreshLists() {
        viewModelScope.launch {
            _state.update { it.copy(listsLoading = true) }
            val lists = repository.importLists()
            val exclusions = repository.exclusions()
            _state.update { s ->
                var next = s.copy(listsLoading = false)
                when (lists) {
                    is ArrResult.Ok -> next = next.copy(importLists = lists.value)
                    is ArrResult.Err -> if (next.importLists.isEmpty()) {
                        next = next.copy(listsError = lists.error)
                    }
                }
                when (exclusions) {
                    is ArrResult.Ok -> next = next.copy(exclusions = exclusions.value)
                    is ArrResult.Err -> {
                        val everythingEmpty = next.exclusions.isEmpty() &&
                            next.importLists.isEmpty()
                        if (everythingEmpty) next = next.copy(listsError = exclusions.error)
                    }
                }
                if (lists is ArrResult.Ok || exclusions is ArrResult.Ok) {
                    next = next.copy(listsError = null)
                }
                next
            }
        }
    }

    fun openAddForm(series: SeriesResource) {
        val current = _state.value
        _state.update {
            it.copy(
                form = AddSeriesForm(
                    series = series,
                    rootFolderPath = current.rootFolders.firstOrNull()?.path.orEmpty(),
                    qualityProfileId = current.qualityProfiles.firstOrNull()?.id ?: 0,
                    languageProfileId = current.languageProfiles.firstOrNull()?.id ?: 0,
                    monitoredSeasons = SeasonMonitoring.defaultMonitored(
                        MonitorTypes.ALL,
                        SeasonMonitoring.seasonNumbersOf(series),
                    ),
                ),
            )
        }
    }

    fun closeAddForm() = _state.update { it.copy(form = null) }

    fun updateAddForm(transform: (AddSeriesForm) -> AddSeriesForm) =
        _state.update { s ->
            val form = s.form ?: return@update s
            s.copy(form = transform(form))
        }

    /**
     * Switches the monitor strategy and recomputes the season picker from it,
     * mirroring how Sonarr's web add dialog resets the checkboxes when the
     * strategy changes.
     */
    fun setMonitorStrategy(strategy: MonitorTypes) = updateAddForm { form ->
        form.copy(
            monitor = strategy,
            monitoredSeasons = SeasonMonitoring.defaultMonitored(
                strategy,
                SeasonMonitoring.seasonNumbersOf(form.series),
            ),
        )
    }

    fun toggleSeason(seasonNumber: Int) = updateAddForm { form ->
        val next = if (seasonNumber in form.monitoredSeasons) {
            form.monitoredSeasons - seasonNumber
        } else {
            form.monitoredSeasons + seasonNumber
        }
        form.copy(monitoredSeasons = next)
    }

    fun toggleTag(tagId: Int) = updateAddForm { form ->
        val next = if (tagId in form.tagIds) form.tagIds - tagId else form.tagIds + tagId
        form.copy(tagIds = next)
    }

    /**
     * Adds the series in the open form. [searchForMissing] true sends the
     * add+search variant. The POST body reuses the lookup result so tvdbId,
     * seasons, and images stay server-authoritative.
     */
    fun submitAdd(searchForMissing: Boolean) {
        val form = _state.value.form ?: return
        if (form.rootFolderPath.isBlank()) return
        val base = form.series
        val body = base.copy(
            id = 0,
            monitored = form.monitored,
            qualityProfileId = form.qualityProfileId,
            languageProfileId = form.languageProfileId,
            rootFolderPath = form.rootFolderPath,
            seriesType = form.seriesType,
            seasonFolder = form.seasonFolder,
            tags = form.tagIds,
            seasons = base.seasons.orEmpty().map { season ->
                season.copy(monitored = season.seasonNumber in form.monitoredSeasons)
            },
            addOptions = AddSeriesOptions(
                monitor = form.monitor,
                searchForMissingEpisodes = searchForMissing,
            ),
        )
        viewModelScope.launch {
            _state.update { it.copy(submitting = true) }
            when (val outcome = repository.addSeries(body)) {
                is ArrResult.Ok -> {
                    val title = base.title.orEmpty()
                    _state.update { s ->
                        s.copy(
                            submitting = false,
                            form = null,
                            message = if (outcome.value is WriteOutcome.Queued) {
                                AddMessage.AddQueued(title)
                            } else {
                                AddMessage.Added(title)
                            },
                        )
                    }
                }

                is ArrResult.Err -> _state.update {
                    it.copy(submitting = false, message = AddMessage.AddFailed)
                }
            }
        }
    }

    /** Triggers a server-side sync of one import list. */
    fun syncImportList(list: ImportListResource) {
        viewModelScope.launch {
            when (val outcome = repository.syncImportList(list)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        message = if (outcome.value is WriteOutcome.Queued) {
                            AddMessage.ListSyncQueued
                        } else {
                            AddMessage.ListSynced
                        },
                    )
                }

                is ArrResult.Err -> _state.update { it.copy(message = AddMessage.ListSyncFailed) }
            }
        }
    }

    fun openExclusionDraft() = _state.update { it.copy(exclusionDraft = ExclusionDraft()) }

    fun closeExclusionDraft() = _state.update { it.copy(exclusionDraft = null) }

    fun updateExclusionDraft(transform: (ExclusionDraft) -> ExclusionDraft) =
        _state.update { s ->
            val draft = s.exclusionDraft ?: return@update s
            s.copy(exclusionDraft = transform(draft))
        }

    /** Saves the exclusion draft; validation errors flag the fields in place. */
    fun submitExclusion() {
        val draft = _state.value.exclusionDraft ?: return
        val title = draft.title.trim()
        val tvdbId = draft.tvdbText.trim().toIntOrNull()
        if (title.isEmpty() || tvdbId == null || tvdbId <= 0) {
            _state.update {
                it.copy(
                    exclusionDraft = draft.copy(
                        titleError = title.isEmpty(),
                        tvdbError = tvdbId == null || tvdbId <= 0,
                    ),
                )
            }
            return
        }
        viewModelScope.launch {
            when (val outcome = repository.addExclusion(tvdbId, title)) {
                is ArrResult.Ok -> _state.update { s ->
                    s.copy(
                        exclusionDraft = null,
                        message = if (outcome.value is WriteOutcome.Queued) {
                            AddMessage.ExclusionQueued
                        } else {
                            AddMessage.ExclusionSaved
                        },
                    )
                }

                is ArrResult.Err -> _state.update { it.copy(message = AddMessage.ExclusionFailed) }
            }
        }
    }

    fun deleteExclusion(exclusion: ImportListExclusionResource) {
        viewModelScope.launch {
            when (val outcome = repository.deleteExclusion(exclusion)) {
                is ArrResult.Ok -> _state.update { s ->
                    s.copy(
                        message = if (outcome.value is WriteOutcome.Queued) {
                            AddMessage.ExclusionQueued
                        } else {
                            AddMessage.ExclusionDeleted
                        },
                    )
                }

                is ArrResult.Err -> _state.update { it.copy(message = AddMessage.ExclusionFailed) }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private companion object {
        /** Matches Sonarr's web search-as-you-type settle time. */
        const val SEARCH_DEBOUNCE_MILLIS = 400L
    }
}
