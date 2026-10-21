package dev.appolon.homelab.arr.feature.bazarr.episodes.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.bazarr.episodes.data.BazarrSubtitlesRepository
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrLanguageOption
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrLanguageRef
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrProviderResult
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrSubtitleTrack
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

/**
 * State of the movie subtitle screen: the same per-language breakdown as the
 * episode list, flattened onto one media row.
 */
data class MovieSubtitlesUiState(
    val loaded: Boolean = false,
    val loadError: ArrError? = null,
    val title: String = "",
    val year: Int? = null,
    val audioLanguages: List<BazarrLanguageRef> = emptyList(),
    val subtitles: List<BazarrSubtitleTrack> = emptyList(),
    val missingLanguages: List<BazarrLanguageRef> = emptyList(),
    val languages: List<BazarrLanguageOption> = emptyList(),
    val queued: Boolean = false,
    val busy: Boolean = false,
    val refreshing: Boolean = false,
    val manualSearch: ManualSearchState? = null,
    val viewer: ViewerState? = null,
    val upload: UploadState? = null,
    val message: SubtitlesMessage? = null,
)

/**
 * ViewModel of the movie subtitle screen for one Radarr movie. Owns no
 * Android types so unit tests construct it directly against fakes.
 */
class MovieSubtitlesViewModel(
    private val radarrId: Int,
    private val repository: BazarrSubtitlesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MovieSubtitlesUiState())
    val state: StateFlow<MovieSubtitlesUiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<MovieSubtitlesUiState>> = state
        .map { s ->
            when {
                s.loadError != null && !s.loaded -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                else -> LceState.Content(s, refreshing = s.refreshing)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeMovie(radarrId)
                .catch { t -> _state.update { it.copy(loaded = true, loadError = t.arrError()) } }
                .collect { movie ->
                    _state.update {
                        it.copy(
                            loaded = movie != null,
                            title = movie?.title.orEmpty(),
                            year = movie?.year,
                            audioLanguages = movie?.audioLanguages.orEmpty(),
                            subtitles = movie?.subtitles.orEmpty(),
                            missingLanguages = movie?.missingLanguages.orEmpty(),
                        )
                    }
                }
        }
        viewModelScope.launch {
            repository.queuedMovieIds()
                .catch { }
                .collect { ids -> _state.update { it.copy(queued = radarrId in ids) } }
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
            when (val result = repository.refreshMovie(radarrId)) {
                is ArrResult.Ok -> _state.update { it.copy(refreshing = false, loadError = null) }
                is ArrResult.Err ->
                    _state.update { it.copy(refreshing = false, loadError = result.error) }
            }
        }
    }

    /** Asks Bazarr for the best subtitle in one missing language. */
    fun downloadSubtitle(language: BazarrLanguageRef) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val outcome = repository.downloadMovieSubtitle(
                radarrId = radarrId,
                languageCode2 = language.code2,
                forced = language.forced,
                hearingImpaired = language.hearingImpaired,
            )
            _state.update { it.copy(busy = false, message = outcome.message(
                SubtitlesMessage.DownloadSynced,
                SubtitlesMessage.DownloadQueued,
            )) }
            if (outcome is ArrResult.Ok) repository.refreshMovie(radarrId)
        }
    }

    fun deleteSubtitle(track: BazarrSubtitleTrack) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val outcome = repository.deleteMovieSubtitle(
                radarrId = radarrId,
                languageCode2 = track.languageCode2,
                forced = track.forced,
                hearingImpaired = track.hearingImpaired,
                path = track.path,
            )
            _state.update { it.copy(busy = false, message = outcome.message(
                SubtitlesMessage.DeleteSynced,
                SubtitlesMessage.DeleteQueued,
            )) }
        }
    }

    fun openManualSearch() {
        _state.update { it.copy(manualSearch = ManualSearchState(mediaId = radarrId, mediaLabel = "")) }
        searchManual()
    }

    fun retryManualSearch() = searchManual()

    private fun searchManual() {
        viewModelScope.launch {
            _state.update { it.copy(manualSearch = it.manualSearch?.copy(loading = true, error = null)) }
            when (val result = repository.manualSearchMovie(radarrId)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(manualSearch = it.manualSearch?.copy(loading = false, results = result.value))
                }

                is ArrResult.Err -> _state.update {
                    it.copy(manualSearch = it.manualSearch?.copy(loading = false, error = result.error))
                }
            }
        }
    }

    fun closeManualSearch() = _state.update { it.copy(manualSearch = null) }

    /** Downloads one manual-search hit for this movie. */
    fun downloadManualResult(result: BazarrProviderResult) {
        viewModelScope.launch {
            _state.update {
                it.copy(manualSearch = it.manualSearch?.copy(downloadingKey = "${result.provider}|${result.subtitleId}"))
            }
            val outcome = repository.downloadManualMovie(radarrId = radarrId, result = result)
            _state.update { s ->
                s.copy(
                    manualSearch = s.manualSearch?.copy(downloadingKey = null),
                    message = outcome.message(SubtitlesMessage.DownloadSynced, SubtitlesMessage.DownloadQueued),
                )
            }
            if (outcome is ArrResult.Ok) {
                repository.refreshMovie(radarrId)
                _state.update { it.copy(manualSearch = null) }
            }
        }
    }

    fun openViewer(track: BazarrSubtitleTrack) {
        val label = _state.value.title
        _state.update { it.copy(viewer = ViewerState(title = label, path = track.path, loading = true)) }
        viewModelScope.launch {
            when (val result = repository.subtitleContents(track.path)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(viewer = it.viewer?.copy(loading = false, content = result.value))
                }

                is ArrResult.Err -> _state.update {
                    it.copy(viewer = it.viewer?.copy(loading = false, error = result.error))
                }
            }
        }
    }

    fun closeViewer() = _state.update { it.copy(viewer = null) }

    fun openUpload() = _state.update {
        it.copy(upload = UploadState(languageCode2 = it.missingLanguages.firstOrNull()?.code2.orEmpty()))
    }

    fun updateUpload(transform: (UploadState) -> UploadState) = _state.update { s ->
        s.upload?.let { s.copy(upload = transform(it)) } ?: s
    }

    /** Submits the upload sheet; online-only, failures surface directly. */
    fun submitUpload(bytes: ByteArray) {
        val form = _state.value.upload ?: return
        if (!form.ready) return
        viewModelScope.launch {
            _state.update { it.copy(upload = form.copy(submitting = true)) }
            val outcome = repository.uploadMovieSubtitle(
                radarrId = radarrId,
                languageCode2 = form.languageCode2,
                forced = form.forced,
                hearingImpaired = form.hearingImpaired,
                fileName = form.fileName,
                bytes = bytes,
            )
            when (outcome) {
                is ArrResult.Ok -> {
                    _state.update { it.copy(upload = null, message = SubtitlesMessage.UploadSynced) }
                    repository.refreshMovie(radarrId)
                }

                is ArrResult.Err -> _state.update { s ->
                    s.copy(
                        upload = (s.upload ?: form).copy(submitting = false),
                        message = SubtitlesMessage.WriteFailed(outcome.error),
                    )
                }
            }
        }
    }

    fun closeUpload() = _state.update { it.copy(upload = null) }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
