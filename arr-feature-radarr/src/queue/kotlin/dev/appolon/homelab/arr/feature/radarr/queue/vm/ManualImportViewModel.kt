package dev.appolon.homelab.arr.feature.radarr.queue.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.radarr.model.ManualImportResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.radarr.queue.data.ImportFolder
import dev.appolon.homelab.arr.feature.radarr.queue.data.QueueWriteOutcome
import dev.appolon.homelab.arr.feature.radarr.queue.data.RadarrManualImportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Transient import results, mapped to strings by the screen. */
enum class ManualImportNotice {
    IMPORTED,
    QUEUED,
}

/** Section-level load state for the folder browser and the candidate list. */
sealed interface ImportLoad {
    data object Loading : ImportLoad
    data object Ready : ImportLoad
    data class Failed(val error: ArrError) : ImportLoad
}

/**
 * Interactive manual import: browse folders (or start from a queue item's
 * download), toggle the parsed files whose movie match is right, and import
 * the selection. Selections without a matched movie are refused because the
 * endpoint cannot import them.
 */
class ManualImportViewModel(
    private val repository: RadarrManualImportRepository,
    downloadId: String?,
    outputPath: String?,
) : ViewModel() {

    data class UiState(
        val downloadId: String? = null,
        val folder: String? = null,
        val folders: List<ImportFolder> = emptyList(),
        val foldersLoad: ImportLoad = ImportLoad.Ready,
        val items: List<ManualImportResource> = emptyList(),
        val itemsLoad: ImportLoad = ImportLoad.Ready,
        val selected: Set<Int> = emptySet(),
        val importing: Boolean = false,
        val notice: ManualImportNotice? = null,
        val importedCount: Int = 0,
        val writeError: ArrError? = null,
    ) {
        val selectionIncomplete: Boolean
            get() = selected.any { id -> items.find { it.id == id }?.movie?.id == 0 }

        val canImport: Boolean get() = selected.isNotEmpty() && !selectionIncomplete
    }

    private val _state = MutableStateFlow(UiState(downloadId = downloadId, folder = outputPath))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        if (downloadId != null) {
            loadDownloadItems(downloadId, outputPath)
        } else {
            browse(null)
        }
    }

    /** Lists the importable files of [folder]. */
    fun loadFolderItems(folder: String) {
        _state.update { it.copy(folder = folder, itemsLoad = ImportLoad.Loading, selected = emptySet()) }
        viewModelScope.launch {
            when (val result = repository.listFolderItems(folder)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(items = result.value, itemsLoad = ImportLoad.Ready)
                }

                is ArrResult.Err -> _state.update {
                    it.copy(itemsLoad = ImportLoad.Failed(result.error), items = emptyList())
                }
            }
        }
    }

    /** Lists a download's files; used on entry from a queue row. */
    fun loadDownloadItems(downloadId: String, outputPath: String?) {
        _state.update {
            it.copy(
                downloadId = downloadId,
                folder = outputPath,
                itemsLoad = ImportLoad.Loading,
                selected = emptySet(),
            )
        }
        viewModelScope.launch {
            when (val result = repository.listDownloadItems(downloadId, outputPath)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(items = result.value, itemsLoad = ImportLoad.Ready)
                }

                is ArrResult.Err -> _state.update {
                    it.copy(itemsLoad = ImportLoad.Failed(result.error), items = emptyList())
                }
            }
        }
    }

    /** Navigates the folder browser; null lists the server's root view. */
    fun browse(path: String?) {
        _state.update { it.copy(foldersLoad = ImportLoad.Loading) }
        viewModelScope.launch {
            when (val result = repository.listFolders(path)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(folders = result.value, foldersLoad = ImportLoad.Ready)
                }

                is ArrResult.Err -> _state.update {
                    it.copy(foldersLoad = ImportLoad.Failed(result.error), folders = emptyList())
                }
            }
        }
    }

    fun toggle(itemId: Int) {
        _state.update { s ->
            s.copy(
                selected = if (itemId in s.selected) s.selected - itemId else s.selected + itemId,
            )
        }
    }

    fun selectAll() {
        _state.update { s -> s.copy(selected = s.items.map { it.id }.toSet()) }
    }

    fun clearSelection() {
        _state.update { it.copy(selected = emptySet()) }
    }

    /** Imports every selected item; offline the POST is queued. */
    fun importSelected() {
        val state = _state.value
        if (!state.canImport || state.importing) return
        val selections = state.items.filter { it.id in state.selected }
        _state.update { it.copy(importing = true) }
        viewModelScope.launch {
            when (val result = repository.importSelections(selections)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        importing = false,
                        notice = if (result.value is QueueWriteOutcome.Queued) {
                            ManualImportNotice.QUEUED
                        } else {
                            ManualImportNotice.IMPORTED
                        },
                        importedCount = selections.size,
                        writeError = null,
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(importing = false, writeError = result.error, notice = null)
                }
            }
        }
    }

    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun consumeWriteError() {
        _state.update { it.copy(writeError = null) }
    }
}
