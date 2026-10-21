package dev.appolon.homelab.arr.feature.radarr.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.QueuedAction
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * One-shot messages surfaced as snackbars or navigation events by the
 * screen. Pure data so tests can assert them without Android resources.
 */
sealed interface DetailMessage {
    data object EditSaved : DetailMessage
    data object EditQueued : DetailMessage
    data object RenameStarted : DetailMessage
    data object RenameQueued : DetailMessage
    data object SearchStarted : DetailMessage
    data object SearchQueued : DetailMessage
    data object DeleteQueued : DetailMessage
    data object Deleted : DetailMessage
    data class WriteFailed(val error: ArrError) : DetailMessage
}

/** State of the movie detail screen; every section renders its own LCE. */
data class RadarrMovieDetailUiState(
    val movie: LceState<MovieDetailUi> = LceState.Loading,
    val files: LceState<List<MovieFileUi>> = LceState.Loading,
    val cast: LceState<List<CreditUi>> = LceState.Loading,
    val crew: LceState<List<CreditUi>> = LceState.Loading,
    val history: LceState<List<HistoryUi>> = LceState.Loading,
    val extraFiles: LceState<List<ExtraFileUi>> = LceState.Loading,
    val editOptions: EditOptionsUi = EditOptionsUi(),
    val queuedActions: List<QueuedAction> = emptyList(),
    val imageClient: OkHttpClient? = null,
    val refreshing: Boolean = false,
    val working: Boolean = false,
    val message: DetailMessage? = null,
) {
    val anyQueued: Boolean get() = queuedActions.isNotEmpty()
}

/**
 * Loads one movie's sections through the cache-first repository and performs
 * its writes (edit, delete, rename, search). Optimistic offline writes show
 * through [RadarrMovieDetailUiState.queuedActions] collected from the
 * [ActionQueue]; the store observation keeps the header in sync with replays.
 */
class RadarrMovieDetailViewModel(
    private val serverId: ServerId,
    private val movieId: Int,
    private val repository: RadarrMovieDetailRepository,
    queue: ActionQueue,
    private val imageClientSource: suspend () -> OkHttpClient? = { null },
) : ViewModel() {

    private val _state = MutableStateFlow(RadarrMovieDetailUiState())
    val state: StateFlow<RadarrMovieDetailUiState> = _state.asStateFlow()

    /** Latest wire movie, used to build full-resource write payloads. */
    private var currentMovie: MovieResource? = null
    private var baseUrl: String = ""

    init {
        viewModelScope.launch {
            imageClientSource()?.let { client ->
                _state.update { it.copy(imageClient = client) }
            }
        }
        viewModelScope.launch {
            repository.observeMovie(serverId, movieId).collect { movie ->
                if (movie != null) mapMovie(movie)
            }
        }
        viewModelScope.launch {
            queue.observe().collect { actions ->
                _state.update {
                    it.copy(queuedActions = RadarrDetailLogic.actionsForMovie(actions, movieId))
                }
            }
        }
        load(force = false)
    }

    /** Pull-to-refresh: reloads every section, bypassing the cache TTL. */
    fun refresh() = load(force = true)

    fun load(force: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            baseUrl = repository.baseUrl(serverId).orEmpty()
            when (val movieResult = repository.movie(serverId, movieId, force)) {
                is ArrResult.Ok -> {
                    currentMovie = movieResult.value
                    _state.update {
                        it.copy(movie = LceState.Content(movieResult.value.toMovieDetailUi(baseUrl)))
                    }
                }
                is ArrResult.Err -> {
                    _state.update { state ->
                        state.copy(
                            refreshing = false,
                            movie = LceState.Error(movieResult.error),
                            files = LceState.Error(movieResult.error),
                            cast = LceState.Error(movieResult.error),
                            crew = LceState.Error(movieResult.error),
                            history = LceState.Error(movieResult.error),
                            extraFiles = LceState.Error(movieResult.error),
                        )
                    }
                    return@launch
                }
            }
            val options = repository.editOptions(serverId, force)
            val files = repository.files(serverId, movieId, force)
            val credits = repository.credits(serverId, movieId, force)
            val extras = repository.extraFiles(serverId, movieId, force)
            val history = repository.history(serverId, movieId, force)
            val cast = RadarrDetailLogic.castOf(credits.valueOrDefault(emptyList()))
                .map { it.toCreditUi(baseUrl) }
            val crew = RadarrDetailLogic.crewOf(credits.valueOrDefault(emptyList()))
                .map { it.toCreditUi(baseUrl) }
            _state.update { state ->
                state.copy(
                    editOptions = options,
                    files = section(files) { list -> list.map { it.toMovieFileUi() } },
                    cast = section(credits) { cast },
                    crew = section(credits) { crew },
                    extraFiles = section(extras) { list -> list.map { it.toExtraFileUi() } },
                    history = section(history) { list -> list.map { it.toHistoryUi() } },
                    refreshing = false,
                )
            }
        }
    }

    /** Monitor toggle: the fastest path through the same write pipeline. */
    fun toggleMonitored() {
        val movie = currentMovie ?: return
        val edits = EditableMovieUi.from(movie).copy(monitored = !movie.monitored)
        saveEdits(edits, moveFiles = false)
    }

    fun saveEdits(edits: EditableMovieUi, moveFiles: Boolean) {
        val movie = currentMovie ?: return
        viewModelScope.launch {
            _state.update { it.copy(working = true) }
            val outcome = repository.updateMovie(serverId, movie, edits, moveFiles)
            currentMovie = outcome.value ?: currentMovie
            _state.update {
                it.copy(
                    working = false,
                    message = when {
                        outcome.error != null -> DetailMessage.WriteFailed(outcome.error)
                        outcome.queued -> DetailMessage.EditQueued
                        else -> DetailMessage.EditSaved
                    },
                )
            }
        }
    }

    fun deleteMovie(deleteFiles: Boolean, addImportExclusion: Boolean) {
        val movie = currentMovie ?: return
        viewModelScope.launch {
            _state.update { it.copy(working = true) }
            val outcome = repository.deleteMovie(serverId, movie, deleteFiles, addImportExclusion)
            _state.update {
                it.copy(
                    working = false,
                    message = when {
                        outcome.error != null -> DetailMessage.WriteFailed(outcome.error)
                        outcome.queued -> DetailMessage.DeleteQueued
                        else -> DetailMessage.Deleted
                    },
                )
            }
        }
    }

    fun startRename() = runCommand(
        online = DetailMessage.RenameStarted,
        offline = DetailMessage.RenameQueued,
    ) { repository.renameFiles(serverId, it) }

    fun startSearch() = runCommand(
        online = DetailMessage.SearchStarted,
        offline = DetailMessage.SearchQueued,
    ) { repository.searchMovie(serverId, it) }

    private fun runCommand(
        online: DetailMessage,
        offline: DetailMessage,
        block: suspend (MovieResource) -> WriteOutcome<*>,
    ) {
        val movie = currentMovie ?: return
        viewModelScope.launch {
            _state.update { it.copy(working = true) }
            val outcome = block(movie)
            _state.update {
                it.copy(
                    working = false,
                    message = when {
                        outcome.error != null -> DetailMessage.WriteFailed(outcome.error)
                        outcome.queued -> offline
                        else -> online
                    },
                )
            }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun mapMovie(movie: MovieResource) {
        _state.update { state ->
            state.copy(movie = LceState.Content(movie.toMovieDetailUi(baseUrl)))
        }
    }

    private fun <T, U> section(result: ArrResult<T>, map: (T) -> U): LceState<U> =
        when (result) {
            is ArrResult.Ok -> LceState.Content(map(result.value))
            is ArrResult.Err -> LceState.Error(result.error)
        }
}
