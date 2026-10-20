package dev.appolon.homelab.arr.feature.bazarr.episodes.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.bazarr.episodes.data.BazarrSubtitlesRepository
import dev.appolon.homelab.arr.feature.bazarr.episodes.data.WriteOutcome
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrEpisodeSubtitles
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
 * State of the per-series episode subtitle list. Rows carry the SxxEyy code,
 * every attached track with language, hearing impaired flag, and score, plus
 * the missing languages with a download action each. Language filter chips
 * come from the Bazarr languages endpoint.
 */
data class EpisodeSubtitlesUiState(
    val loaded: Boolean = false,
    val loadError: ArrError? = null,
    val seriesTitle: String = "",
    val episodes: List<BazarrEpisodeSubtitles> = emptyList(),
    val languages: List<BazarrLanguageOption> = emptyList(),
    val selectedLanguage: String? = null,
    val missingOnly: Boolean = false,
    val queuedEpisodeIds: Set<Int> = emptySet(),
    val busyEpisodeIds: Set<Int> = emptySet(),
    val refreshing: Boolean = false,
    val manualSearch: ManualSearchState? = null,
    val viewer: ViewerState? = null,
    val upload: UploadState? = null,
    val uploadEpisodeId: Int? = null,
    val message: SubtitlesMessage? = null,
) {
    /** Episodes after the language chip and missing-only filters. */
    val presented: List<BazarrEpisodeSubtitles>
        get() = episodes.filter { episode ->
            val missingOnlyMatch = !missingOnly || episode.missingLanguages.isNotEmpty()
            val languageMatch = selectedLanguage?.let { code ->
                episode.missingLanguages.any { it.code2 == code } ||
                    episode.subtitles.any { it.languageCode2 == code }
            } ?: true
            missingOnlyMatch && languageMatch
        }
}

/**
 * ViewModel of the episode subtitle list for one series. Owns no Android
 * types so unit tests construct it directly against fakes.
 */
class EpisodeSubtitlesViewModel(
    private val seriesId: Int,
    private val repository: BazarrSubtitlesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EpisodeSubtitlesUiState())
    val state: StateFlow<EpisodeSubtitlesUiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<EpisodeSubtitlesUiState>> = state
        .map { s ->
            when {
                s.loadError != null && s.episodes.isEmpty() -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.episodes.isEmpty() -> LceState.Empty
                else -> LceState.Content(s, refreshing = s.refreshing)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeEpisodes(seriesId)
                .catch { t -> _state.update { it.copy(loaded = true, loadError = t.arrError()) } }
                .collect { episodes -> _state.update { it.copy(episodes = episodes, loaded = true) } }
        }
        viewModelScope.launch {
            repository.queuedEpisodeIds()
                .catch { }
                .collect { ids -> _state.update { it.copy(queuedEpisodeIds = ids) } }
        }
        viewModelScope.launch {
            val title = repository.seriesTitle(seriesId)
            _state.update { it.copy(seriesTitle = title) }
        }
        viewModelScope.launch { refreshLanguages() }
        refresh()
    }

    /** Pull-to-refresh and error retry entry point. */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            when (val result = repository.refreshEpisodes(seriesId)) {
                is ArrResult.Ok -> _state.update { it.copy(refreshing = false, loadError = null) }
                is ArrResult.Err ->
                    _state.update { it.copy(refreshing = false, loadError = result.error) }
            }
        }
    }

    fun setLanguageFilter(code2: String?) =
        _state.update { it.copy(selectedLanguage = if (it.selectedLanguage == code2) null else code2) }

    fun toggleMissingOnly() = _state.update { it.copy(missingOnly = !it.missingOnly) }

    /** Asks Bazarr for the best subtitle in one missing language. */
    fun downloadSubtitle(episode: BazarrEpisodeSubtitles, language: BazarrLanguageRef) {
        viewModelScope.launch {
            markBusy(episode.episodeId, true)
            val outcome = repository.downloadEpisodeSubtitle(
                seriesId = seriesId,
                episodeId = episode.episodeId,
                languageCode2 = language.code2,
                forced = language.forced,
                hearingImpaired = language.hearingImpaired,
            )
            markBusy(episode.episodeId, false)
            applyWrite(outcome, SubtitlesMessage.DownloadSynced, SubtitlesMessage.DownloadQueued)
            if (outcome is ArrResult.Ok) repository.refreshEpisodes(seriesId)
        }
    }

    fun deleteSubtitle(episode: BazarrEpisodeSubtitles, track: BazarrSubtitleTrack) {
        viewModelScope.launch {
            markBusy(episode.episodeId, true)
            val outcome = repository.deleteEpisodeSubtitle(
                seriesId = seriesId,
                episodeId = episode.episodeId,
                languageCode2 = track.languageCode2,
                forced = track.forced,
                hearingImpaired = track.hearingImpaired,
                path = track.path,
            )
            markBusy(episode.episodeId, false)
            applyWrite(outcome, SubtitlesMessage.DeleteSynced, SubtitlesMessage.DeleteQueued)
        }
    }

    fun openManualSearch(episode: BazarrEpisodeSubtitles) {
        _state.update {
            it.copy(
                manualSearch = ManualSearchState(
                    mediaId = episode.episodeId,
                    mediaLabel = "${episode.code} ${episode.title}".trim(),
                ),
            )
        }
        searchManual(episode.episodeId)
    }

    fun retryManualSearch() {
        _state.value.manualSearch?.let { searchManual(it.mediaId) }
    }

    private fun searchManual(episodeId: Int) {
        viewModelScope.launch {
            _state.update { it.copy(manualSearch = it.manualSearch?.copy(loading = true, error = null)) }
            when (val result = repository.manualSearchEpisode(episodeId)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        manualSearch = it.manualSearch?.copy(loading = false, results = result.value),
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(manualSearch = it.manualSearch?.copy(loading = false, error = result.error))
                }
            }
        }
    }

    fun closeManualSearch() = _state.update { it.copy(manualSearch = null) }

    /** Downloads one manual-search hit for the episode the sheet is open on. */
    fun downloadManualResult(result: BazarrProviderResult) {
        val search = _state.value.manualSearch ?: return
        viewModelScope.launch {
            _state.update {
                it.copy(manualSearch = it.manualSearch?.copy(downloadingKey = search.resultKey(result)))
            }
            val outcome = repository.downloadManualEpisode(
                seriesId = seriesId,
                episodeId = search.mediaId,
                result = result,
            )
            _state.update { s ->
                s.copy(
                    manualSearch = s.manualSearch?.copy(downloadingKey = null),
                    message = outcome.message(
                        SubtitlesMessage.DownloadSynced,
                        SubtitlesMessage.DownloadQueued,
                    ),
                )
            }
            if (outcome is ArrResult.Ok) {
                repository.refreshEpisodes(seriesId)
                _state.update { it.copy(manualSearch = null) }
            }
        }
    }

    fun openViewer(track: BazarrSubtitleTrack, mediaLabel: String) {
        _state.update {
            it.copy(viewer = ViewerState(title = mediaLabel, path = track.path, loading = true))
        }
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

    fun openUpload(episode: BazarrEpisodeSubtitles) = _state.update {
        it.copy(
            upload = UploadState(languageCode2 = episode.missingLanguages.firstOrNull()?.code2.orEmpty()),
            uploadEpisodeId = episode.episodeId,
        )
    }

    fun updateUpload(transform: (UploadState) -> UploadState) = _state.update { s ->
        s.upload?.let { s.copy(upload = transform(it)) } ?: s
    }

    /** Submits the upload sheet; online-only, failures surface directly. */
    fun submitUpload(bytes: ByteArray) {
        val form = _state.value.upload ?: return
        val episodeId = _state.value.uploadEpisodeId ?: return
        if (!form.ready) return
        viewModelScope.launch {
            _state.update { it.copy(upload = form.copy(submitting = true)) }
            val outcome = repository.uploadEpisodeSubtitle(
                seriesId = seriesId,
                episodeId = episodeId,
                languageCode2 = form.languageCode2,
                forced = form.forced,
                hearingImpaired = form.hearingImpaired,
                fileName = form.fileName,
                bytes = bytes,
            )
            when (outcome) {
                is ArrResult.Ok -> {
                    _state.update {
                        it.copy(upload = null, uploadEpisodeId = null, message = SubtitlesMessage.UploadSynced)
                    }
                    repository.refreshEpisodes(seriesId)
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

    fun closeUpload() = _state.update { it.copy(upload = null, uploadEpisodeId = null) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun applyWrite(
        outcome: ArrResult<WriteOutcome>,
        synced: SubtitlesMessage,
        queued: SubtitlesMessage,
    ) {
        _state.update { it.copy(message = outcome.message(synced, queued)) }
    }

    private fun markBusy(episodeId: Int, busy: Boolean) = _state.update { s ->
        s.copy(
            busyEpisodeIds = if (busy) s.busyEpisodeIds + episodeId else s.busyEpisodeIds - episodeId,
        )
    }

    private suspend fun refreshLanguages() {
        when (val result = repository.languages()) {
            is ArrResult.Ok -> _state.update { it.copy(languages = result.value) }
            is ArrResult.Err -> Unit
        }
    }
}

/** Snackbar message for any write outcome. */
internal fun ArrResult<WriteOutcome>.message(
    synced: SubtitlesMessage,
    queued: SubtitlesMessage,
): SubtitlesMessage = when (this) {
    is ArrResult.Ok -> if (value is WriteOutcome.Queued) queued else synced
    is ArrResult.Err -> SubtitlesMessage.WriteFailed(error)
}
