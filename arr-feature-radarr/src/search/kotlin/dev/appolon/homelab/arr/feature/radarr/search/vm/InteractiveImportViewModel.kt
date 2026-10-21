package dev.appolon.homelab.arr.feature.radarr.search.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.radarr.model.Language
import dev.appolon.homelab.arr.api.radarr.model.ManualImportResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.Quality
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.radarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.radarr.search.data.ImportDecision
import dev.appolon.homelab.arr.feature.radarr.search.data.ImportFolder
import dev.appolon.homelab.arr.feature.radarr.search.data.RadarrInteractiveImportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One-shot import outcomes, mapped to strings by the screen. */
sealed interface ImportFlowMessage {
    data object Imported : ImportFlowMessage
    data object ImportQueued : ImportFlowMessage
    data class ImportFailed(val error: ArrError) : ImportFlowMessage
}

/** Section-level load state for the folder browser and the file list. */
sealed interface ImportFlowLoad {
    data object Loading : ImportFlowLoad
    data object Ready : ImportFlowLoad
    data class Failed(val error: ArrError) : ImportFlowLoad
}

/** One file row with its current per-file decisions. */
data class ImportFileUi(
    val id: Int,
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val matchedTitle: String?,
    val qualityName: String?,
    val languageName: String?,
    val rejections: List<String>,
    val selected: Boolean,
    /** Non-null while the decision editor is open on this file. */
    val editing: Boolean = false,
)

/** UI state of the interactive import decision flow. */
data class InteractiveImportUiState(
    val movie: MovieResource? = null,
    val movieLoad: ImportFlowLoad = ImportFlowLoad.Loading,
    val folder: String? = null,
    val folders: List<ImportFolder> = emptyList(),
    val foldersLoad: ImportFlowLoad = ImportFlowLoad.Loading,
    val files: List<ImportFileUi> = emptyList(),
    val filesLoad: ImportFlowLoad = ImportFlowLoad.Ready,
    val qualityOptions: List<Quality> = emptyList(),
    val languageOptions: List<Language> = emptyList(),
    val editingFileId: Int? = null,
    val approving: Boolean = false,
    val notice: ImportFlowMessage? = null,
) {
    val selectedCount: Int get() = files.count { it.selected }
    val canApprove: Boolean get() = selectedCount > 0 && !approving
}

/**
 * Interactive import decision flow for one movie: browse folders, list the
 * parsed candidate files, choose quality and language per file, then approve
 * the import. Every approved file imports as this screen's movie even when
 * the server matched a different one, which is the decision the user is
 * making here.
 */
class InteractiveImportViewModel(
    private val movieId: Int,
    private val repository: RadarrInteractiveImportRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(InteractiveImportUiState())
    val state: StateFlow<InteractiveImportUiState> = _state.asStateFlow()

    /** Pending per-file decisions keyed by file id; null keeps what Radarr parsed. */
    private val decisions = HashMap<Int, ImportDecision>()
    private var resources: Map<Int, ManualImportResource> = emptyMap()

    init {
        loadMovie()
    }

    /** (Re)loads the movie and its decision options; also the retry path. */
    fun loadMovie() {
        _state.update { it.copy(movieLoad = ImportFlowLoad.Loading) }
        viewModelScope.launch {
            when (val result = repository.movie(movieId)) {
                is ArrResult.Ok -> {
                    _state.update {
                        it.copy(
                            movie = result.value,
                            movieLoad = ImportFlowLoad.Ready,
                            qualityOptions = repository.qualityOptions(result.value),
                            languageOptions = repository.languageOptions(),
                        )
                    }
                    // Start browsing inside the movie's own folder when it has one.
                    if (_state.value.folder == null) {
                        browse(result.value.path)
                    }
                }

                is ArrResult.Err ->
                    _state.update { it.copy(movieLoad = ImportFlowLoad.Failed(result.error)) }
            }
        }
    }

    /** Navigates the folder browser; null lists the server's root view. */
    fun browse(path: String?) {
        _state.update { it.copy(folder = path, foldersLoad = ImportFlowLoad.Loading) }
        viewModelScope.launch {
            when (val result = repository.listFolders(path)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(folders = result.value, foldersLoad = ImportFlowLoad.Ready)
                }

                is ArrResult.Err -> _state.update {
                    it.copy(
                        foldersLoad = ImportFlowLoad.Failed(result.error),
                        folders = emptyList(),
                    )
                }
            }
        }
    }

    fun retryFolders() {
        browse(_state.value.folder)
    }

    /** Loads the import candidates of [folder] and preselects every file. */
    fun pickFolder(folder: String) {
        _state.update { it.copy(folder = folder, filesLoad = ImportFlowLoad.Loading) }
        viewModelScope.launch {
            when (val result = repository.listFiles(folder)) {
                is ArrResult.Ok -> {
                    resources = result.value.associateBy { it.id }
                    _state.update { state ->
                        state.copy(
                            files = result.value.map { it.toFileUi(selected = true) },
                            filesLoad = ImportFlowLoad.Ready,
                        )
                    }
                }

                is ArrResult.Err -> _state.update {
                    it.copy(filesLoad = ImportFlowLoad.Failed(result.error), files = emptyList())
                }
            }
        }
    }

    fun retryFiles() {
        _state.value.folder?.let(::pickFolder)
    }

    fun toggleFile(id: Int) {
        _state.update { state ->
            state.copy(
                files = state.files.map {
                    if (it.id == id) it.copy(selected = !it.selected) else it
                },
            )
        }
    }

    fun selectAll(selected: Boolean) {
        _state.update { state ->
            state.copy(files = state.files.map { it.copy(selected = selected) })
        }
    }

    fun openEditor(id: Int) {
        _state.update { state ->
            state.copy(editingFileId = id)
        }
    }

    fun closeEditor() {
        _state.update { it.copy(editingFileId = null) }
    }

    /** Saves one file's quality and language decision; null keeps the parsed value. */
    fun applyDecision(id: Int, quality: Quality?, language: Language?) {
        val resource = resources[id] ?: return
        decisions[id] = ImportDecision(
            resource = resource,
            quality = quality,
            language = language,
            selected = true,
        )
        _state.update { state ->
            state.copy(
                editingFileId = null,
                files = state.files.map { file ->
                    if (file.id != id) {
                        file
                    } else {
                        file.copy(
                            qualityName = quality?.name ?: file.qualityName,
                            languageName = language?.name ?: file.languageName,
                            selected = true,
                        )
                    }
                },
            )
        }
    }

    /** Approves the selection; offline the import queues for replay. */
    fun approve() {
        val movie = _state.value.movie ?: return
        val current = _state.value
        if (!current.canApprove) return
        val chosen = current.files.filter { it.selected }.mapNotNull { file ->
            decisions[file.id] ?: resources[file.id]?.let { resource ->
                ImportDecision(
                    resource = resource,
                    quality = null,
                    language = null,
                    selected = true,
                )
            }
        }
        _state.update { it.copy(approving = true) }
        viewModelScope.launch {
            val outcome = repository.importSelections(movie, chosen)
            _state.update { state ->
                state.copy(
                    approving = false,
                    notice = when (outcome) {
                        is ArrResult.Ok -> when (outcome.value) {
                            WriteOutcome.Synced -> ImportFlowMessage.Imported
                            is WriteOutcome.Queued -> ImportFlowMessage.ImportQueued
                        }

                        is ArrResult.Err -> ImportFlowMessage.ImportFailed(outcome.error)
                    },
                )
            }
        }
    }

    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    private fun ManualImportResource.toFileUi(selected: Boolean): ImportFileUi = ImportFileUi(
        id = id,
        path = path.orEmpty(),
        name = relativePath ?: name ?: path.orEmpty(),
        sizeBytes = size,
        matchedTitle = movie.title,
        qualityName = quality.quality?.name,
        languageName = languages.orEmpty().firstOrNull()?.name,
        rejections = rejections.orEmpty().mapNotNull { it.reason }.filter { it.isNotBlank() },
        selected = selected,
    )
}
