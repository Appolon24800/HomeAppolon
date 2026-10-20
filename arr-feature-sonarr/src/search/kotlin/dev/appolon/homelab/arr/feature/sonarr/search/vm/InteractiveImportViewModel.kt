package dev.appolon.homelab.arr.feature.sonarr.search.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.sonarr.model.Language
import dev.appolon.homelab.arr.api.sonarr.model.ManualImportResource
import dev.appolon.homelab.arr.api.sonarr.model.Quality
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.sonarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.sonarr.search.data.ImportDecision
import dev.appolon.homelab.arr.feature.sonarr.search.data.ImportFolder
import dev.appolon.homelab.arr.feature.sonarr.search.data.SonarrInteractiveImportRepository
import dev.appolon.homelab.arr.feature.sonarr.search.data.seriesTitleOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

/** One-shot import outcomes, mapped to strings by the screen. */
sealed interface ImportFlowMessage {
    data object Imported : ImportFlowMessage
    data object ImportQueued : ImportFlowMessage
    data class ImportFailed(val error: ArrError) : ImportFlowMessage
}

/** Section-level load state for the series header, folder browser, and file list. */
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
    val matchedEpisodesLabel: String?,
    val qualityName: String?,
    val languageName: String?,
    val rejections: List<String>,
    val selected: Boolean,
)

/** UI state of the interactive import decision flow. */
data class InteractiveImportUiState(
    val series: SeriesResource? = null,
    val seriesLoad: ImportFlowLoad = ImportFlowLoad.Loading,
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
 * Interactive import decision flow for one series (optionally pinned to one
 * season): browse folders, list the parsed candidate files, choose quality
 * and language per file, then approve the import. Every approved file
 * imports as this screen's series even when the server matched a different
 * one, which is the decision the user is making here.
 */
class InteractiveImportViewModel(
    private val seriesId: Int,
    private val repository: SonarrInteractiveImportRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(InteractiveImportUiState())
    val state: StateFlow<InteractiveImportUiState> = _state.asStateFlow()

    /** Pending per-file decisions keyed by file id; null keeps what Sonarr parsed. */
    private val decisions = HashMap<Int, ImportDecision>()
    private var resources: Map<Int, ManualImportResource> = emptyMap()

    init {
        loadSeries()
    }

    /** (Re)loads the series and its decision options; also the retry path. */
    fun loadSeries() {
        _state.update { it.copy(seriesLoad = ImportFlowLoad.Loading) }
        viewModelScope.launch {
            when (val result = repository.series(seriesId)) {
                is ArrResult.Ok -> {
                    _state.update {
                        it.copy(
                            series = result.value,
                            seriesLoad = ImportFlowLoad.Ready,
                            qualityOptions = repository.qualityOptions(result.value),
                            languageOptions = repository.languageOptions(),
                        )
                    }
                    // Start browsing inside the series' own folder when it has one.
                    if (_state.value.folder == null) {
                        browse(result.value.path)
                    }
                }

                is ArrResult.Err ->
                    _state.update { it.copy(seriesLoad = ImportFlowLoad.Failed(result.error)) }
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
        _state.update { state -> state.copy(editingFileId = id) }
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
        val series = _state.value.series ?: return
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
            val outcome = repository.importSelections(series, chosen)
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
        matchedTitle = seriesTitleOrNull(),
        matchedEpisodesLabel = matchedLabelOrNull(),
        qualityName = quality.quality.name,
        languageName = languages.orEmpty().firstOrNull()?.name,
        rejections = rejections.orEmpty().mapNotNull { it.reason }.filter { it.isNotBlank() },
        selected = selected,
    )

    /** "S02E05" from the parsed season and episodes; the season alone when episodes are missing. */
    private fun ManualImportResource.matchedLabelOrNull(): String? {
        val season = seasonNumber ?: return null
        val episodeNumbers = episodes.orEmpty().mapNotNull { episode ->
            (episode["episodeNumber"] as? JsonPrimitive)?.content?.toIntOrNull()
        }
        val first = episodeNumbers.firstOrNull() ?: return null
        val label = "S" + season.toString().padStart(2, '0') +
            "E" + first.toString().padStart(2, '0')
        val more = episodeNumbers.size - 1
        return if (more > 0) "$label (+$more)" else label
    }
}
