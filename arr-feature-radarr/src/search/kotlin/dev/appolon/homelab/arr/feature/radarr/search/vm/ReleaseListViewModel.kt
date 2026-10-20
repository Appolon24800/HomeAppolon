package dev.appolon.homelab.arr.feature.radarr.search.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.radarr.model.Language
import dev.appolon.homelab.arr.api.radarr.model.Quality
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.radarr.search.data.RadarrReleaseRepository
import dev.appolon.homelab.arr.feature.radarr.search.data.RadarrReleaseResource
import dev.appolon.homelab.arr.feature.radarr.search.data.ReleaseRowUi
import dev.appolon.homelab.arr.feature.radarr.search.data.ReleaseSort
import dev.appolon.homelab.arr.feature.radarr.search.data.releaseRow
import dev.appolon.homelab.arr.feature.radarr.search.data.sortReleaseRows
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One-shot grab outcomes, mapped to strings by the screen. */
sealed interface GrabMessage {
    data object Grabbed : GrabMessage
    data object GrabQueued : GrabMessage
    data class GrabFailed(val error: ArrError) : GrabMessage
}

/** Options the grab sheet offers for one release. */
data class GrabOptions(
    val qualities: List<Quality> = emptyList(),
    val languages: List<Language> = emptyList(),
)

/** The release the grab sheet is currently attached to, if any. */
data class GrabTarget(
    val row: ReleaseRowUi,
    val options: GrabOptions,
)

/** UI state of the release list: LCE rows plus sort, expansion, and grab flow. */
data class ReleaseListUiState(
    val releases: LceState<List<ReleaseRowUi>> = LceState.Loading,
    val sort: ReleaseSort = ReleaseSort.QUALITY_SCORE,
    val expandedGuids: Set<String> = emptySet(),
    val grabTarget: GrabTarget? = null,
    val grabbing: Boolean = false,
    val message: GrabMessage? = null,
    /** True when cached rows are showing because the latest refresh failed. */
    val stale: Boolean = false,
)

/**
 * Interactive search for one movie: cached release list with TTL refresh,
 * client-side sorting, expandable rejection reasons, and the grab flow with
 * quality and language overrides.
 */
class ReleaseListViewModel(
    private val movieId: Int,
    private val repository: RadarrReleaseRepository,
    private val grabOptionsSource: suspend () -> GrabOptions,
) : ViewModel() {

    private val _state = MutableStateFlow(ReleaseListUiState())
    val state: StateFlow<ReleaseListUiState> = _state.asStateFlow()

    /** Movie ids with a queued grab, for the per-row queued indicator. */
    val queuedMovieIds: StateFlow<Set<Int>> = repository.queuedMovieIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        load()
    }

    fun refresh() {
        viewModelScope.launch {
            markRefreshing()
            when (val result = repository.refresh(movieId)) {
                is ArrResult.Ok -> publishRows(result.value, stale = false)
                is ArrResult.Err -> publishLoadError(result.error)
            }
        }
    }

    fun retry() {
        load()
    }

    fun setSort(sort: ReleaseSort) {
        _state.update { it.copy(sort = sort) }
        resort()
    }

    fun toggleRejections(guid: String) {
        _state.update { state ->
            state.copy(
                expandedGuids = if (guid in state.expandedGuids) {
                    state.expandedGuids - guid
                } else {
                    state.expandedGuids + guid
                },
            )
        }
    }

    fun openGrabSheet(row: ReleaseRowUi) {
        viewModelScope.launch {
            val options = grabOptionsSource()
            _state.update { it.copy(grabTarget = GrabTarget(row, options)) }
        }
    }

    fun dismissGrabSheet() {
        _state.update { it.copy(grabTarget = null) }
    }

    /**
     * Grabs the sheet's release with the chosen overrides. Offline grabs
     * queue; the message tells the user which happened.
     */
    fun grab(quality: Quality?, language: Language?) {
        val target = _state.value.grabTarget ?: return
        if (_state.value.grabbing) return
        _state.update { it.copy(grabbing = true) }
        viewModelScope.launch {
            val outcome = repository.grabRelease(
                movieId = movieId,
                release = target.row.release,
                qualityOverride = quality,
                languageOverride = language,
            )
            _state.update { state ->
                state.copy(
                    grabbing = false,
                    grabTarget = null,
                    message = when (outcome) {
                        is ArrResult.Ok -> when (outcome.value) {
                            WriteOutcome.Synced -> GrabMessage.Grabbed
                            is WriteOutcome.Queued -> GrabMessage.GrabQueued
                        }

                        is ArrResult.Err -> GrabMessage.GrabFailed(outcome.error)
                    },
                )
            }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun load() {
        viewModelScope.launch {
            val cached = repository.cachedReleases(movieId)
            if (cached.isNotEmpty()) markRefreshing()
            when (val result = repository.releases(movieId)) {
                is ArrResult.Ok -> publishRows(result.value, stale = false)

                is ArrResult.Err -> {
                    if (cached.isNotEmpty()) {
                        publishRows(cached, stale = true)
                    } else {
                        publishLoadError(result.error)
                    }
                }
            }
        }
    }

    private fun markRefreshing() {
        _state.update { state ->
            val content = state.releases as? LceState.Content ?: return@update state
            state.copy(releases = content.copy(refreshing = true))
        }
    }

    private fun publishRows(rows: List<RadarrReleaseResource>, stale: Boolean) {
        _state.update { state ->
            val mapped = rows.map(::releaseRow)
            val releases = when {
                mapped.isEmpty() && stale && state.releases is LceState.Content ->
                    state.releases

                mapped.isEmpty() -> LceState.Empty

                else -> LceState.Content(sortReleaseRows(mapped, state.sort))
            }
            state.copy(releases = releases, stale = stale)
        }
    }

    private fun publishLoadError(error: ArrError) {
        _state.update { it.copy(releases = LceState.Error(error), stale = false) }
    }

    private fun resort() {
        _state.update { state ->
            val content = state.releases as? LceState.Content ?: return@update state
            state.copy(releases = LceState.Content(sortReleaseRows(content.data, state.sort)))
        }
    }
}
