package dev.appolon.homelab.arr.feature.lidarr.search.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.ManualImportResource
import dev.appolon.homelab.arr.api.lidarr.model.Quality
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.lidarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.lidarr.search.data.ImportDecision
import dev.appolon.homelab.arr.feature.lidarr.search.data.ImportFolder
import dev.appolon.homelab.arr.feature.lidarr.search.data.LidarrInteractiveImportRepository
import dev.appolon.homelab.arr.feature.lidarr.search.data.albumTitleOrNull
import dev.appolon.homelab.arr.feature.lidarr.search.data.artistTitleOrNull
import dev.appolon.homelab.arr.feature.lidarr.search.data.hasCompleteMatch
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

/** Section-level load state for the artist header, folder browser, and file list. */
sealed interface ImportFlowLoad {
    data object Loading : ImportFlowLoad
    data object Ready : ImportFlowLoad
    data class Failed(val error: ArrError) : ImportFlowLoad
}

/** One file row with its current per-file decision state. */
data class ImportFileUi(
    val id: Int,
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val matchedArtistTitle: String?,
    val matchedAlbumTitle: String?,
    val trackCount: Int,
    val qualityName: String?,
    val rejections: List<String>,
    val selected: Boolean,
    /** False when Lidarr could not resolve artist, album, and release. */
    val matched: Boolean,
)

/** UI state of the interactive import decision flow. */
data class InteractiveImportUiState(
    val artist: ArtistResource? = null,
    val artistLoad: ImportFlowLoad = ImportFlowLoad.Loading,
    val folder: String? = null,
    val folders: List<ImportFolder> = emptyList(),
    val foldersLoad: ImportFlowLoad = ImportFlowLoad.Loading,
    val files: List<ImportFileUi> = emptyList(),
    val filesLoad: ImportFlowLoad = ImportFlowLoad.Ready,
    val qualityOptions: List<Quality> = emptyList(),
    val editingFileId: Int? = null,
    val approving: Boolean = false,
    val notice: ImportFlowMessage? = null,
) {
    val selectedCount: Int get() = files.count { it.selected }
    val canApprove: Boolean get() = selectedCount > 0 && !approving
}

/**
 * Interactive import decision flow for one artist: browse folders, list the
 * parsed candidate files, choose the quality per file, then approve the
 * import. Only files with a complete artist, album, and release match are
 * selectable, because the import body replays each file's own match; the
 * decision the user makes here is the quality.
 */
class InteractiveImportViewModel(
    private val artistId: Int,
    private val repository: LidarrInteractiveImportRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(InteractiveImportUiState())
    val state: StateFlow<InteractiveImportUiState> = _state.asStateFlow()

    /** Pending per-file decisions keyed by file id; null quality keeps the parsed one. */
    private val decisions = HashMap<Int, ImportDecision>()
    private var resources: Map<Int, ManualImportResource> = emptyMap()

    init {
        loadArtist()
    }

    /** (Re)loads the artist and its quality options; also the retry path. */
    fun loadArtist() {
        _state.update { it.copy(artistLoad = ImportFlowLoad.Loading) }
        viewModelScope.launch {
            when (val result = repository.artist(artistId)) {
                is ArrResult.Ok -> {
                    _state.update {
                        it.copy(
                            artist = result.value,
                            artistLoad = ImportFlowLoad.Ready,
                            qualityOptions = repository.qualityOptions(result.value),
                        )
                    }
                    // Start browsing inside the artist's own folder when it has one.
                    if (_state.value.folder == null) {
                        browse(result.value.path)
                    }
                }

                is ArrResult.Err ->
                    _state.update { it.copy(artistLoad = ImportFlowLoad.Failed(result.error)) }
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

    /** Loads the import candidates of [folder]; complete matches start selected. */
    fun pickFolder(folder: String) {
        _state.update { it.copy(folder = folder, filesLoad = ImportFlowLoad.Loading) }
        viewModelScope.launch {
            when (val result = repository.listFiles(folder)) {
                is ArrResult.Ok -> {
                    resources = result.value.associateBy { it.id }
                    _state.update { state ->
                        state.copy(
                            files = result.value.map { it.toFileUi() },
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

    /** Unmatched rows refuse the toggle; they have nothing to import under. */
    fun toggleFile(id: Int) {
        _state.update { state ->
            state.copy(
                files = state.files.map {
                    if (it.id == id && it.matched) it.copy(selected = !it.selected) else it
                },
            )
        }
    }

    fun selectAll(selected: Boolean) {
        _state.update { state ->
            state.copy(
                files = state.files.map { if (it.matched) it.copy(selected = selected) else it },
            )
        }
    }

    fun openEditor(id: Int) {
        _state.update { state -> state.copy(editingFileId = id) }
    }

    fun closeEditor() {
        _state.update { it.copy(editingFileId = null) }
    }

    /** Saves one file's quality decision; null keeps the parsed value. */
    fun applyDecision(id: Int, quality: Quality?) {
        val resource = resources[id] ?: return
        decisions[id] = ImportDecision(
            resource = resource,
            quality = quality,
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
                            selected = true,
                        )
                    }
                },
            )
        }
    }

    /** Approves the selection; offline the import queues for replay. */
    fun approve() {
        val artist = _state.value.artist
        val current = _state.value
        if (artist == null || !current.canApprove) return
        val chosen = current.files.filter { it.selected }.mapNotNull { file ->
            decisions[file.id] ?: resources[file.id]?.takeIf { it.hasCompleteMatch() }?.let { resource ->
                ImportDecision(
                    resource = resource,
                    quality = null,
                    selected = true,
                )
            }
        }
        _state.update { it.copy(approving = true) }
        viewModelScope.launch {
            val outcome = repository.importSelections(chosen)
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

    private fun ManualImportResource.toFileUi(): ImportFileUi = ImportFileUi(
        id = id,
        path = path.orEmpty(),
        name = name ?: path?.substringAfterLast('/') ?: path.orEmpty(),
        sizeBytes = size,
        matchedArtistTitle = artistTitleOrNull(),
        matchedAlbumTitle = albumTitleOrNull(),
        trackCount = tracks.orEmpty().size,
        qualityName = quality?.quality?.name,
        rejections = rejections.orEmpty().mapNotNull { it.reason }.filter { it.isNotBlank() },
        selected = hasCompleteMatch(),
        matched = hasCompleteMatch(),
    )
}
