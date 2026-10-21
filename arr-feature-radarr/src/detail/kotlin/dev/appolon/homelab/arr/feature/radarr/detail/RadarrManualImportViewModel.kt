package dev.appolon.homelab.arr.feature.radarr.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.radarr.model.ManualImportReprocessResource
import dev.appolon.homelab.arr.api.radarr.model.ManualImportResource
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One import candidate shown in the interactive import list. */
data class ManualImportItemUi(
    val id: Int,
    val path: String,
    val relativePath: String,
    val sizeBytes: Long,
    val qualityName: String?,
    val languages: String,
    val releaseGroup: String?,
    val rejections: String,
    val selected: Boolean,
)

/** One-shot outcomes of the import screen. */
sealed interface ImportMessage {
    data object ImportStarted : ImportMessage
    data object ImportQueued : ImportMessage
    data class ImportFailed(val error: dev.appolon.homelab.arr.core.ArrError) : ImportMessage
}

data class RadarrManualImportUiState(
    val items: LceState<List<ManualImportItemUi>> = LceState.Loading,
    val folder: String? = null,
    val importing: Boolean = false,
    val queued: Boolean = false,
    val message: ImportMessage? = null,
)

/**
 * Interactive import for one movie's folder: lists candidate files Radarr
 * parsed (with their rejections), tracks the selection, and kicks off a
 * `DownloadedMoviesScan` of the folder; offline the scan command queues for
 * replay like every other write.
 */
class RadarrManualImportViewModel(
    private val serverId: ServerId,
    private val movieId: Int,
    private val repository: RadarrMovieDetailRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RadarrManualImportUiState())
    val state: StateFlow<RadarrManualImportUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val movie = repository.movie(serverId, movieId, force = false)
            val folder = (movie as? ArrResult.Ok)?.value?.path
            if (folder.isNullOrBlank()) {
                // Without a path there is nothing to scan; the empty state
                // explains that the movie has no folder yet.
                _state.update { it.copy(items = LceState.Empty) }
                return@launch
            }
            load(folder)
        }
    }

    fun refresh() {
        val folder = _state.value.folder ?: return
        viewModelScope.launch { load(folder, force = true) }
    }

    fun toggle(itemId: Int) {
        _state.update { state ->
            val content = state.items as? LceState.Content ?: return@update state
            state.copy(
                items = LceState.Content(
                    content.data.map { item ->
                        if (item.id == itemId) item.copy(selected = !item.selected) else item
                    },
                ),
            )
        }
    }

    fun selectAll(selected: Boolean) {
        _state.update { state ->
            val content = state.items as? LceState.Content ?: return@update state
            state.copy(
                items = LceState.Content(content.data.map { it.copy(selected = selected) }),
            )
        }
    }

    /**
     * Re-parses the current selection server-side so rejection reasons stay
     * current before the user commits the import.
     */
    fun revalidate() {
        val folder = _state.value.folder ?: return
        val selected = (_state.value.items as? LceState.Content)?.data
            ?.filter { it.selected } ?: return
        if (selected.isEmpty()) return
        viewModelScope.launch {
            val payload = selected.map { item ->
                ManualImportReprocessResource(id = item.id, path = item.path, movieId = movieId)
            }
            when (val result = repository.reprocess(serverId, payload)) {
                is ArrResult.Ok -> _state.update { state ->
                    val content = state.items as? LceState.Content ?: return@update state
                    val byId = result.value.associateBy { it.id }
                    state.copy(
                        items = LceState.Content(
                            content.data.map { item ->
                                val updated = byId[item.id] ?: return@map item
                                item.copy(
                                    qualityName = updated.quality.quality?.name ?: item.qualityName,
                                    releaseGroup = updated.releaseGroup ?: item.releaseGroup,
                                    rejections = updated.rejections.orEmpty()
                                        .mapNotNull { it.reason }
                                        .joinToString("\n"),
                                )
                            },
                        ),
                    )
                }
                is ArrResult.Err -> _state.update {
                    it.copy(message = ImportMessage.ImportFailed(result.error))
                }
            }
        }
    }

    /** Starts the folder scan that performs the import; queues when offline. */
    fun importSelected() {
        val folder = _state.value.folder ?: return
        viewModelScope.launch {
            _state.update { it.copy(importing = true) }
            val outcome = repository.importFolder(serverId, folder)
            _state.update {
                it.copy(
                    importing = false,
                    message = when {
                        outcome.error != null -> ImportMessage.ImportFailed(outcome.error)
                        outcome.queued -> ImportMessage.ImportQueued
                        else -> ImportMessage.ImportStarted
                    },
                )
            }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private suspend fun load(folder: String, force: Boolean = false) {
        _state.update { it.copy(items = LceState.Loading, folder = folder) }
        when (val result = repository.manualImportItems(serverId, folder, movieId)) {
            is ArrResult.Ok -> _state.update { state ->
                val items = result.value.map { it.toItemUi() }
                state.copy(items = LceState.Content(items))
            }
            is ArrResult.Err -> _state.update {
                it.copy(items = LceState.Error(result.error))
            }
        }
    }

    private fun ManualImportResource.toItemUi(): ManualImportItemUi = ManualImportItemUi(
        id = id,
        path = path.orEmpty(),
        relativePath = relativePath ?: name ?: path.orEmpty(),
        sizeBytes = size,
        qualityName = quality.quality?.name,
        languages = languages.orEmpty().mapNotNull { it.name }.joinToString(", "),
        releaseGroup = releaseGroup,
        rejections = rejections.orEmpty().mapNotNull { it.reason }.joinToString("\n"),
        selected = true,
    )
}
