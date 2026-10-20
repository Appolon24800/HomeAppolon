package dev.appolon.homelab.arr.feature.radarr.settings.connect.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.radarr.model.ImportListConfigResource
import dev.appolon.homelab.arr.api.radarr.model.ImportListMoviesResource
import dev.appolon.homelab.arr.api.radarr.model.ImportListResource
import dev.appolon.homelab.arr.api.radarr.model.ProviderTestAllResult
import dev.appolon.homelab.arr.api.radarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.radarr.settings.connect.data.RadarrConnectRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Content payload of the import lists LCE state. */
data class RadarrImportListsContent(
    val lists: List<ImportListResource>,
    val tags: List<TagResource>,
    val queuedRemovedIds: Set<Int>,
    val pendingActions: Int,
    val testResults: Map<Int, ProviderTestAllResult>,
)

/** Transient results of import list actions, mapped to strings by the screen. */
enum class ImportListsNotice {
    DELETED,
    DELETED_QUEUED,
    TEST_ALL_DONE,
    SYNC_STARTED,
    SYNC_FAILED,
    OPTIONS_SAVED,
    OPTIONS_SAVED_QUEUED,
}

/**
 * Import lists list state: cached rows with enable/auto badges, test-all
 * verdicts, the ImportListSync trigger, the sync preview (what the lists
 * currently discover, with existing and excluded flags), and the list
 * options config (clean library level).
 */
class RadarrImportListsViewModel(
    private val serverId: ServerId,
    private val repository: RadarrConnectRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val lists: List<ImportListResource> = emptyList(),
        val tags: List<TagResource> = emptyList(),
        val queuedRemovedIds: Set<Int> = emptySet(),
        val pendingActions: Int = 0,
        val options: ImportListConfigResource? = null,
        val testResults: Map<Int, ProviderTestAllResult> = emptyMap(),
        val testingAll: Boolean = false,
        val syncing: Boolean = false,
        val preview: ImportListPreviewState? = null,
        val confirmDeleteFor: ImportListResource? = null,
        val writeError: ArrError? = null,
        val notice: ImportListsNotice? = null,
        val busy: Boolean = false,
    )

    /** Sync preview sheet state: cached instantly, refreshed when online. */
    data class ImportListPreviewState(
        val loading: Boolean = false,
        val error: ArrError? = null,
        val movies: List<ImportListMoviesResource> = emptyList(),
        val tagById: Map<Int, String> = emptyMap(),
    ) {
        val addableCount: Int get() = movies.count { !it.isExisting && !it.isExcluded }
        val existingCount: Int get() = movies.count { it.isExisting }
        val excludedCount: Int get() = movies.count { it.isExcluded }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<RadarrImportListsContent>> = state
        .map { s ->
            when {
                s.loadError != null && s.lists.isEmpty() -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.lists.isEmpty() -> LceState.Empty
                else -> LceState.Content(
                    RadarrImportListsContent(
                        lists = s.lists,
                        tags = s.tags,
                        queuedRemovedIds = s.queuedRemovedIds,
                        pendingActions = s.pendingActions,
                        testResults = s.testResults,
                    ),
                    refreshing = s.refreshing,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeImportLists().collect { lists ->
                _state.update { it.copy(loaded = true, loadError = null, lists = lists) }
            }
        }
        viewModelScope.launch {
            repository.observeTags().collect { tags -> _state.update { it.copy(tags = tags) } }
        }
        viewModelScope.launch {
            repository.queuedRemovedIds(RadarrConnectRepository.IMPORT_LIST_PATH).collect { ids ->
                _state.update { it.copy(queuedRemovedIds = ids) }
            }
        }
        viewModelScope.launch {
            repository.queuedCount().collect { pending ->
                _state.update { it.copy(pendingActions = pending) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            when (val result = repository.refreshIfStale()) {
                is ArrResult.Ok -> _state.update {
                    it.copy(refreshing = false, options = it.options ?: repository.cachedImportListConfig())
                }

                is ArrResult.Err -> _state.update {
                    it.copy(
                        loaded = true,
                        loadError = if (it.lists.isEmpty()) result.error else null,
                        writeError = if (it.lists.isEmpty()) null else result.error,
                        refreshing = false,
                    )
                }
            }
        }
    }

    fun testAll() {
        viewModelScope.launch {
            _state.update { it.copy(testingAll = true) }
            when (val result = repository.testAllImportLists()) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        testingAll = false,
                        testResults = result.value.associateBy { verdict -> verdict.id },
                        notice = ImportListsNotice.TEST_ALL_DONE,
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(testingAll = false, writeError = result.error)
                }
            }
        }
    }

    /** Triggers the server-side sync command; the preview refreshes after. */
    fun sync() {
        viewModelScope.launch {
            _state.update { it.copy(syncing = true) }
            when (val result = repository.syncImportLists()) {
                is ArrResult.Ok -> _state.update {
                    it.copy(syncing = false, notice = ImportListsNotice.SYNC_STARTED)
                }

                is ArrResult.Err -> _state.update {
                    it.copy(syncing = false, notice = ImportListsNotice.SYNC_FAILED, writeError = result.error)
                }
            }
            loadPreview()
        }
    }

    fun openPreview() {
        _state.update { it.copy(preview = ImportListPreviewState(loading = true)) }
        loadPreview()
    }

    fun closePreview() {
        _state.update { it.copy(preview = null) }
    }

    private fun loadPreview() {
        viewModelScope.launch {
            val tagById = _state.value.tags.associate { it.id to it.label.orEmpty() }
            when (val result = repository.importListPreview()) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        preview = ImportListPreviewState(
                            movies = result.value,
                            tagById = tagById,
                        ),
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(
                        preview = it.preview?.copy(loading = false, error = result.error)
                            ?: ImportListPreviewState(error = result.error),
                    )
                }
            }
        }
    }

    fun requestDelete(list: ImportListResource) {
        _state.update { it.copy(confirmDeleteFor = list) }
    }

    fun deleteCancelled() {
        _state.update { it.copy(confirmDeleteFor = null) }
    }

    fun deleteConfirmed() {
        val target = _state.value.confirmDeleteFor ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, confirmDeleteFor = null) }
            when (val result = repository.removeImportList(target.id)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        busy = false,
                        notice = if (result.value is WriteOutcome.Queued) {
                            ImportListsNotice.DELETED_QUEUED
                        } else {
                            ImportListsNotice.DELETED
                        },
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(busy = false, writeError = result.error)
                }
            }
        }
    }

    fun updateOptions(config: ImportListConfigResource) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            when (val result = repository.saveImportListConfig(config)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        busy = false,
                        options = config,
                        writeError = null,
                        notice = if (result.value is WriteOutcome.Queued) {
                            ImportListsNotice.OPTIONS_SAVED_QUEUED
                        } else {
                            ImportListsNotice.OPTIONS_SAVED
                        },
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(busy = false, writeError = result.error)
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
