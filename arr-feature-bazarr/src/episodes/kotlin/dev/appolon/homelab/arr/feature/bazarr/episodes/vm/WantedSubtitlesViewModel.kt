package dev.appolon.homelab.arr.feature.bazarr.episodes.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.bazarr.episodes.data.BazarrSubtitlesRepository
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrLanguageOption
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrWantedEpisode
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrWantedMovie
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
enum class WantedTab { EPISODES, MOVIES }

/**
 * State of the wanted subtitles screen: episodes and movies missing
 * subtitles, each row carrying its missing languages and a search-and-
 * download action that requests every one of them.
 */
data class WantedSubtitlesUiState(
    val loaded: Boolean = false,
    val loadError: ArrError? = null,
    val tab: WantedTab = WantedTab.EPISODES,
    val episodes: List<BazarrWantedEpisode> = emptyList(),
    val movies: List<BazarrWantedMovie> = emptyList(),
    val languages: List<BazarrLanguageOption> = emptyList(),
    val selectedLanguage: String? = null,
    val queuedEpisodeIds: Set<Int> = emptySet(),
    val queuedMovieIds: Set<Int> = emptySet(),
    val busyEpisodeIds: Set<Int> = emptySet(),
    val busyMovieIds: Set<Int> = emptySet(),
    val refreshing: Boolean = false,
    val message: SubtitlesMessage? = null,
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
}

/**
 * ViewModel of the wanted subtitles list. Search-and-download asks Bazarr for
 * every missing language of one row; offline requests queue per language and
 * the row shows the queued indicator.
 */
class WantedSubtitlesViewModel(
    private val repository: BazarrSubtitlesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WantedSubtitlesUiState())
    val state: StateFlow<WantedSubtitlesUiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<WantedSubtitlesUiState>> = state
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
                is ArrResult.Ok -> _state.update { it.copy(languages = result.value) }
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
            val error = (episodes as? ArrResult.Err)?.error ?: (movies as? ArrResult.Err)?.error
            _state.update { it.copy(refreshing = false, loadError = error) }
        }
    }

    fun setTab(tab: WantedTab) = _state.update { it.copy(tab = tab) }

    fun setLanguageFilter(code2: String?) =
        _state.update { it.copy(selectedLanguage = if (it.selectedLanguage == code2) null else code2) }

    /** Requests every missing language of one wanted episode. */
    fun searchEpisode(row: BazarrWantedEpisode) {
        viewModelScope.launch {
            _state.update { it.copy(busyEpisodeIds = it.busyEpisodeIds + row.episodeId) }
            var anyQueued = false
            var failure: ArrError? = null
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
                    is ArrResult.Ok -> anyQueued = anyQueued || outcome.value is
                        dev.appolon.homelab.arr.feature.bazarr.episodes.data.WriteOutcome.Queued

                    is ArrResult.Err -> if (failure == null) failure = outcome.error
                }
            }
            _state.update {
                it.copy(
                    busyEpisodeIds = it.busyEpisodeIds - row.episodeId,
                    message = failure?.let(SubtitlesMessage::WriteFailed)
                        ?: if (anyQueued) SubtitlesMessage.DownloadQueued else SubtitlesMessage.DownloadSynced,
                )
            }
        }
    }

    /** Requests every missing language of one wanted movie. */
    fun searchMovie(row: BazarrWantedMovie) {
        viewModelScope.launch {
            _state.update { it.copy(busyMovieIds = it.busyMovieIds + row.radarrId) }
            var anyQueued = false
            var failure: ArrError? = null
            row.missingLanguages.forEach { language ->
                when (
                    val outcome = repository.downloadMovieSubtitle(
                        radarrId = row.radarrId,
                        languageCode2 = language.code2,
                        forced = language.forced,
                        hearingImpaired = language.hearingImpaired,
                    )
                ) {
                    is ArrResult.Ok -> anyQueued = anyQueued || outcome.value is
                        dev.appolon.homelab.arr.feature.bazarr.episodes.data.WriteOutcome.Queued

                    is ArrResult.Err -> if (failure == null) failure = outcome.error
                }
            }
            _state.update {
                it.copy(
                    busyMovieIds = it.busyMovieIds - row.radarrId,
                    message = failure?.let(SubtitlesMessage::WriteFailed)
                        ?: if (anyQueued) SubtitlesMessage.DownloadQueued else SubtitlesMessage.DownloadSynced,
                )
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
