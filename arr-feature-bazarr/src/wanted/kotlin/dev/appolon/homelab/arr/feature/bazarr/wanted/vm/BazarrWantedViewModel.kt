package dev.appolon.homelab.arr.feature.bazarr.wanted.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrLanguageOption
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrWantedEpisode
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrWantedMovie
import dev.appolon.homelab.arr.feature.bazarr.wanted.data.BazarrWantedRepository
import dev.appolon.homelab.arr.feature.bazarr.wanted.data.WantedWriteOutcome
import dev.appolon.homelab.arr.feature.bazarr.wanted.model.BazarrWantedBadges
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

/** Which wanted list is showing. */
enum class WantedListTab { EPISODES, MOVIES }

/** Transient result of one search run, surfaced as a snackbar. */
sealed interface WantedMessage {
    data class SearchSynced(val count: Int) : WantedMessage
    data class SearchQueued(val count: Int) : WantedMessage
    data class SearchMixed(val synced: Int, val queued: Int, val failed: Int) : WantedMessage
    data class SearchFailed(val error: ArrError) : WantedMessage
}

/**
 * State of the wanted subtitles screen: episodes and movies missing
 * subtitles with per-row and bulk search-and-download, a language chip
 * filter, and the badges totals line.
 */
data class BazarrWantedUiState(
    val loaded: Boolean = false,
    val loadError: ArrError? = null,
    val tab: WantedListTab = WantedListTab.EPISODES,
    val episodes: List<BazarrWantedEpisode> = emptyList(),
    val movies: List<BazarrWantedMovie> = emptyList(),
    val languages: List<BazarrLanguageOption> = emptyList(),
    val selectedLanguage: String? = null,
    val badges: BazarrWantedBadges? = null,
    val selectedEpisodeIds: Set<Int> = emptySet(),
    val selectedMovieIds: Set<Int> = emptySet(),
    val queuedEpisodeIds: Set<Int> = emptySet(),
    val queuedMovieIds: Set<Int> = emptySet(),
    val busyEpisodeIds: Set<Int> = emptySet(),
    val busyMovieIds: Set<Int> = emptySet(),
    val bulkRunning: Boolean = false,
    val refreshing: Boolean = false,
    val message: WantedMessage? = null,
) {
    /** Wanted episodes after the language chip filter. */
    val presentedEpisodes: List<BazarrWantedEpisode>
        get() = episodes.filter { row ->
            selectedLanguage?.let { code -> row.missingLanguages.any { it.code2 == code } } ?: true
        }

    /** Wanted movies after the language chip filter. */
    val presentedMovies: List<BazarrWantedMovie>
        get() = movies.filter { row ->
            selectedLanguage?.let { code -> row.missingLanguages.any { it.code2 == code } } ?: true
        }

    /** Rows the bulk bar currently acts on. */
    val selectionActive: Boolean
        get() = selectedEpisodeIds.isNotEmpty() || selectedMovieIds.isNotEmpty()

    /** Count shown on the bulk bar. */
    val selectionCount: Int
        get() = if (tab == WantedListTab.EPISODES) selectedEpisodeIds.size else selectedMovieIds.size
}

/**
 * ViewModel of the wanted subtitles list. Search-and-download asks Bazarr for
 * every missing language of one row; the bulk variant iterates the selected
 * rows (or every presented row) sequentially so the server is not hammered.
 * Offline requests queue per language and the rows show the queued indicator.
 */
class BazarrWantedViewModel(
    private val repository: BazarrWantedRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BazarrWantedUiState())
    val state: StateFlow<BazarrWantedUiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<BazarrWantedUiState>> = state
        .map { s ->
            when {
                s.loadError != null && s.episodes.isEmpty() && s.movies.isEmpty() ->
                    LceState.Error(s.loadError)

                !s.loaded -> LceState.Loading
                s.episodes.isEmpty() && s.movies.isEmpty() -> LceState.Empty
                else -> LceState.Content(s, refreshing = s.refreshing)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeWantedEpisodes()
                .catch { t -> _state.update { it.copy(loaded = true, loadError = t.arrError()) } }
                .collect { rows -> _state.update { it.copy(episodes = rows, loaded = true) } }
        }
        viewModelScope.launch {
            repository.observeWantedMovies()
                .catch { }
                .collect { rows -> _state.update { it.copy(movies = rows, loaded = true) } }
        }
        viewModelScope.launch {
            repository.observeBadges()
                .catch { }
                .collect { badges -> _state.update { it.copy(badges = badges) } }
        }
        viewModelScope.launch {
            repository.queuedEpisodeIds()
                .catch { }
                .collect { ids -> _state.update { it.copy(queuedEpisodeIds = ids) } }
        }
        viewModelScope.launch {
            repository.queuedMovieIds()
                .catch { }
                .collect { ids -> _state.update { it.copy(queuedMovieIds = ids) } }
        }
        viewModelScope.launch {
            when (val result = repository.languages()) {
                is ArrResult.Ok ->
                    _state.update { it.copy(languages = result.value) }

                is ArrResult.Err -> Unit
            }
        }
        refresh()
    }

    /** Pull-to-refresh and error retry entry point. */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            val episodes = repository.refreshWantedEpisodes()
            val movies = repository.refreshWantedMovies()
            repository.refreshBadges()
            val error = (episodes as? ArrResult.Err)?.error
                ?: (movies as? ArrResult.Err)?.error
            _state.update { it.copy(refreshing = false, loadError = error) }
        }
    }

    /** Switching tabs drops the selection so the bulk bar never targets a hidden tab. */
    fun setTab(tab: WantedListTab) = _state.update {
        it.copy(
            tab = tab,
            selectedEpisodeIds = emptySet(),
            selectedMovieIds = emptySet(),
        )
    }

    fun setLanguageFilter(code2: String?) =
        _state.update { it.copy(selectedLanguage = if (it.selectedLanguage == code2) null else code2) }

    fun toggleEpisodeSelected(episodeId: Int) = _state.update {
        val next = if (episodeId in it.selectedEpisodeIds) {
            it.selectedEpisodeIds - episodeId
        } else {
            it.selectedEpisodeIds + episodeId
        }
        it.copy(selectedEpisodeIds = next)
    }

    fun toggleMovieSelected(radarrId: Int) = _state.update {
        val next = if (radarrId in it.selectedMovieIds) {
            it.selectedMovieIds - radarrId
        } else {
            it.selectedMovieIds + radarrId
        }
        it.copy(selectedMovieIds = next)
    }

    fun selectAllPresented() = _state.update {
        if (it.tab == WantedListTab.EPISODES) {
            it.copy(selectedEpisodeIds = it.presentedEpisodes.map { row -> row.episodeId }.toSet())
        } else {
            it.copy(selectedMovieIds = it.presentedMovies.map { row -> row.radarrId }.toSet())
        }
    }

    fun clearSelection() = _state.update {
        it.copy(selectedEpisodeIds = emptySet(), selectedMovieIds = emptySet())
    }

    /** Requests every missing language of one wanted episode. */
    fun searchEpisode(row: BazarrWantedEpisode) {
        viewModelScope.launch {
            _state.update { it.copy(busyEpisodeIds = it.busyEpisodeIds + row.episodeId) }
            val result = runEpisodeSearches(listOf(row))
            _state.update {
                it.copy(busyEpisodeIds = it.busyEpisodeIds - row.episodeId, message = result)
            }
        }
    }

    /** Requests every missing language of one wanted movie. */
    fun searchMovie(row: BazarrWantedMovie) {
        viewModelScope.launch {
            _state.update { it.copy(busyMovieIds = it.busyMovieIds + row.radarrId) }
            val result = runMovieSearches(listOf(row))
            _state.update {
                it.copy(busyMovieIds = it.busyMovieIds - row.radarrId, message = result)
            }
        }
    }

    /** Bulk search of the selected rows on the current tab. */
    fun searchSelected() {
        val s = _state.value
        if (s.tab == WantedListTab.EPISODES) {
            val rows = s.presentedEpisodes.filter { it.episodeId in s.selectedEpisodeIds }
            if (rows.isEmpty()) return
            viewModelScope.launch {
                _state.update {
                    it.copy(
                        bulkRunning = true,
                        busyEpisodeIds = it.busyEpisodeIds + rows.map { row -> row.episodeId },
                    )
                }
                val result = runEpisodeSearches(rows)
                _state.update {
                    it.copy(
                        bulkRunning = false,
                        busyEpisodeIds = it.busyEpisodeIds - rows.map { row -> row.episodeId }.toSet(),
                        selectedEpisodeIds = emptySet(),
                        message = result,
                    )
                }
            }
        } else {
            val rows = s.presentedMovies.filter { it.radarrId in s.selectedMovieIds }
            if (rows.isEmpty()) return
            viewModelScope.launch {
                _state.update {
                    it.copy(
                        bulkRunning = true,
                        busyMovieIds = it.busyMovieIds + rows.map { row -> row.radarrId },
                    )
                }
                val result = runMovieSearches(rows)
                _state.update {
                    it.copy(
                        bulkRunning = false,
                        busyMovieIds = it.busyMovieIds - rows.map { row -> row.radarrId }.toSet(),
                        selectedMovieIds = emptySet(),
                        message = result,
                    )
                }
            }
        }
    }

    /** Bulk search of every presented row on the current tab. */
    fun searchAllPresented() {
        val s = _state.value
        if (s.tab == WantedListTab.EPISODES) {
            val rows = s.presentedEpisodes
            if (rows.isEmpty()) return
            viewModelScope.launch {
                _state.update {
                    it.copy(
                        bulkRunning = true,
                        busyEpisodeIds = it.busyEpisodeIds + rows.map { row -> row.episodeId },
                    )
                }
                val result = runEpisodeSearches(rows)
                _state.update {
                    it.copy(
                        bulkRunning = false,
                        busyEpisodeIds = it.busyEpisodeIds - rows.map { row -> row.episodeId }.toSet(),
                        message = result,
                    )
                }
            }
        } else {
            val rows = s.presentedMovies
            if (rows.isEmpty()) return
            viewModelScope.launch {
                _state.update {
                    it.copy(
                        bulkRunning = true,
                        busyMovieIds = it.busyMovieIds + rows.map { row -> row.radarrId },
                    )
                }
                val result = runMovieSearches(rows)
                _state.update {
                    it.copy(
                        bulkRunning = false,
                        busyMovieIds = it.busyMovieIds - rows.map { row -> row.radarrId }.toSet(),
                        message = result,
                    )
                }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /** One search pass over rows: every missing language of every row, sequentially. */
    private suspend fun runEpisodeSearches(rows: List<BazarrWantedEpisode>): WantedMessage {
        var synced = 0
        var queued = 0
        var failed = 0
        var firstError: ArrError? = null
        rows.forEach { row ->
            var rowQueued = false
            var rowFailed = false
            row.missingLanguages.forEach { language ->
                when (
                    val outcome = repository.downloadEpisodeSubtitle(
                        seriesId = row.seriesId,
                        episodeId = row.episodeId,
                        languageCode2 = language.code2,
                        forced = language.forced,
                        hearingImpaired = language.hearingImpaired,
                    )
                ) {
                    is ArrResult.Ok ->
                        rowQueued = rowQueued || outcome.value is WantedWriteOutcome.Queued

                    is ArrResult.Err -> {
                        rowFailed = true
                        if (firstError == null) firstError = outcome.error
                    }
                }
            }
            when {
                rowFailed -> failed++
                rowQueued -> queued++
                else -> synced++
            }
        }
        return summary(synced, queued, failed, firstError)
    }

    /** Movie twin of [runEpisodeSearches]. */
    private suspend fun runMovieSearches(rows: List<BazarrWantedMovie>): WantedMessage {
        var synced = 0
        var queued = 0
        var failed = 0
        var firstError: ArrError? = null
        rows.forEach { row ->
            var rowQueued = false
            var rowFailed = false
            row.missingLanguages.forEach { language ->
                when (
                    val outcome = repository.downloadMovieSubtitle(
                        radarrId = row.radarrId,
                        languageCode2 = language.code2,
                        forced = language.forced,
                        hearingImpaired = language.hearingImpaired,
                    )
                ) {
                    is ArrResult.Ok ->
                        rowQueued = rowQueued || outcome.value is WantedWriteOutcome.Queued

                    is ArrResult.Err -> {
                        rowFailed = true
                        if (firstError == null) firstError = outcome.error
                    }
                }
            }
            when {
                rowFailed -> failed++
                rowQueued -> queued++
                else -> synced++
            }
        }
        return summary(synced, queued, failed, firstError)
    }

    private fun summary(synced: Int, queued: Int, failed: Int, firstError: ArrError?): WantedMessage =
        when {
            failed > 0 && synced + queued == 0 -> WantedMessage.SearchFailed(firstError!!)
            failed > 0 -> WantedMessage.SearchMixed(synced, queued, failed)
            queued > 0 && synced > 0 -> WantedMessage.SearchMixed(synced, queued, 0)
            queued > 0 -> WantedMessage.SearchQueued(queued)
            else -> WantedMessage.SearchSynced(synced)
        }
}
