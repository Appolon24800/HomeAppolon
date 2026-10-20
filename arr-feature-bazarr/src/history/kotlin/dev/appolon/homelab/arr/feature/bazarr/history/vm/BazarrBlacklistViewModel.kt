package dev.appolon.homelab.arr.feature.bazarr.history.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.bazarr.history.data.BazarrHistoryRepository
import dev.appolon.homelab.arr.feature.bazarr.history.data.BlacklistWriteOutcome
import dev.appolon.homelab.arr.feature.bazarr.history.model.BazarrBlacklistEntry
import dev.appolon.homelab.arr.net.arrError
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which blacklist feed is showing. */
enum class BlacklistTab { EPISODES, MOVIES }

/** Transient one-line result of a blacklist write, surfaced as a snackbar. */
sealed interface BlacklistMessage {
    data object RemovedSynced : BlacklistMessage
    data object RemovedQueued : BlacklistMessage
    data object ClearedSynced : BlacklistMessage
    data object ClearedQueued : BlacklistMessage
    data class WriteFailed(val error: ArrError) : BlacklistMessage
}

/**
 * State of the provider blacklist screen: blacklisted episode and movie
 * subtitles with per-entry and whole-tab removal.
 */
data class BazarrBlacklistUiState(
    val loaded: Boolean = false,
    val loadError: ArrError? = null,
    val tab: BlacklistTab = BlacklistTab.EPISODES,
    val episodeEntries: List<BazarrBlacklistEntry> = emptyList(),
    val movieEntries: List<BazarrBlacklistEntry> = emptyList(),
    val busyKeys: Set<String> = emptySet(),
    val queuedKeys: Set<String> = emptySet(),
    val queuedClearEpisodes: Boolean = false,
    val queuedClearMovies: Boolean = false,
    val refreshing: Boolean = false,
    val message: BlacklistMessage? = null,
) {
    /** Rows of the visible tab. */
    val presented: List<BazarrBlacklistEntry>
        get() = if (tab == BlacklistTab.EPISODES) episodeEntries else movieEntries
}

/**
 * ViewModel of the provider blacklist. Removals hit the server first and
 * queue offline; both paths drop the row from the cache so the UI updates
 * immediately, and queued rows keep their indicator until replay.
 */
class BazarrBlacklistViewModel(
    private val repository: BazarrHistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BazarrBlacklistUiState())
    val state: StateFlow<BazarrBlacklistUiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<BazarrBlacklistUiState>> = state
        .map { s ->
            when {
                s.loadError != null && s.episodeEntries.isEmpty() && s.movieEntries.isEmpty() ->
                    LceState.Error(s.loadError)

                !s.loaded -> LceState.Loading
                s.episodeEntries.isEmpty() && s.movieEntries.isEmpty() -> LceState.Empty
                else -> LceState.Content(s, refreshing = s.refreshing)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeEpisodeBlacklist()
                .catch { t -> _state.update { it.copy(loaded = true, loadError = t.arrError()) } }
                .collect { rows -> _state.update { it.copy(episodeEntries = rows, loaded = true) } }
        }
        viewModelScope.launch {
            repository.observeMovieBlacklist()
                .catch { }
                .collect { rows -> _state.update { it.copy(movieEntries = rows, loaded = true) } }
        }
        viewModelScope.launch {
            repository.queuedBlacklistKeys()
                .catch { }
                .collect { keys -> _state.update { it.copy(queuedKeys = keys) } }
        }
        viewModelScope.launch {
            repository.queuedClearAll(isMovie = false)
                .catch { }
                .collect { queued -> _state.update { it.copy(queuedClearEpisodes = queued) } }
        }
        viewModelScope.launch {
            repository.queuedClearAll(isMovie = true)
                .catch { }
                .collect { queued -> _state.update { it.copy(queuedClearMovies = queued) } }
        }
        refresh()
    }

    /** Pull-to-refresh and error retry entry point. */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            when (val result = repository.refreshBlacklist()) {
                is ArrResult.Ok -> _state.update { it.copy(refreshing = false, loadError = null) }
                is ArrResult.Err ->
                    _state.update { it.copy(refreshing = false, loadError = result.error) }
            }
        }
    }

    fun setTab(tab: BlacklistTab) = _state.update { it.copy(tab = tab) }

    /** Removes one blacklisted subtitle from the server. */
    fun remove(entry: BazarrBlacklistEntry) {
        viewModelScope.launch {
            _state.update { it.copy(busyKeys = it.busyKeys + entry.key) }
            val result = if (entry.isMovie) {
                repository.removeMovieBlacklistEntry(entry)
            } else {
                repository.removeEpisodeBlacklistEntry(entry)
            }
            _state.update {
                it.copy(
                    busyKeys = it.busyKeys - entry.key,
                    message = when (result) {
                        is ArrResult.Ok -> when (result.value) {
                            is BlacklistWriteOutcome.Synced -> BlacklistMessage.RemovedSynced
                            is BlacklistWriteOutcome.Queued -> BlacklistMessage.RemovedQueued
                        }

                        is ArrResult.Err -> BlacklistMessage.WriteFailed(result.error)
                    },
                )
            }
        }
    }

    /** Removes every entry of the visible tab. */
    fun clearVisible() {
        viewModelScope.launch {
            val isMovie = _state.value.tab == BlacklistTab.MOVIES
            val result = if (isMovie) {
                repository.clearMovieBlacklist()
            } else {
                repository.clearEpisodeBlacklist()
            }
            _state.update {
                it.copy(
                    message = when (result) {
                        is ArrResult.Ok -> when (result.value) {
                            is BlacklistWriteOutcome.Synced -> BlacklistMessage.ClearedSynced
                            is BlacklistWriteOutcome.Queued -> BlacklistMessage.ClearedQueued
                        }

                        is ArrResult.Err -> BlacklistMessage.WriteFailed(result.error)
                    },
                )
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
