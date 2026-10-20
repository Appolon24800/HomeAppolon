package dev.appolon.homelab.arr.feature.lidarr.settings.connect.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.lidarr.model.ImportListMonitorType
import dev.appolon.homelab.arr.api.lidarr.model.ImportListResource
import dev.appolon.homelab.arr.api.lidarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.data.LidarrConnectRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One list's row in the sync preview sheet. */
data class ImportListSyncRow(
    val name: String,
    val implementationName: String?,
    val automaticAdd: Boolean,
    val searchForNew: Boolean,
    val monitor: ImportListMonitorType?,
    val qualityProfileId: Int,
    val metadataProfileId: Int,
    val rootFolderPath: String?,
    val minRefreshInterval: String?,
)

/** The sync preview sheet payload: what every list would do on a sync. */
data class ImportListSyncPreview(
    val rows: List<ImportListSyncRow> = emptyList(),
    val exclusionCount: Int = 0,
)

/** Content payload of the import lists LCE state. */
data class LidarrImportListsContent(
    val lists: List<ImportListResource>,
    val tags: List<TagResource>,
    val queuedRemovedIds: Set<Int>,
    val pendingActions: Int,
    val preview: ImportListSyncPreview,
)

/** Transient results of import list writes and probes, mapped to strings by the screen. */
enum class ImportListsNotice {
    DELETED,
    DELETED_QUEUED,
    TEST_ALL_DONE,
    SYNC_STARTED,
    SYNC_FAILED,
}

/**
 * Import lists list state. Rows show the automatic-add and search badges,
 * the quality and metadata profiles a synced artist inherits, and tag
 * chips; the app bar offers test-all, the server-side sync command, the
 * sync preview sheet built from the cached lists, and exclusions. Lidarr
 * has no import list config endpoint, so per-list options live on each
 * list's editor instead of a group options card.
 */
class LidarrImportListsViewModel(
    private val serverId: ServerId,
    private val repository: LidarrConnectRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val lists: List<ImportListResource> = emptyList(),
        val tags: List<TagResource> = emptyList(),
        val queuedRemovedIds: Set<Int> = emptySet(),
        val pendingActions: Int = 0,
        val exclusions: Int = 0,
        val previewOpen: Boolean = false,
        val testingAll: Boolean = false,
        val syncing: Boolean = false,
        val confirmDeleteFor: ImportListResource? = null,
        val writeError: ArrError? = null,
        val notice: ImportListsNotice? = null,
        val busy: Boolean = false,
    ) {
        val preview: ImportListSyncPreview
            get() = ImportListSyncPreview(
                rows = lists.map { list ->
                    ImportListSyncRow(
                        name = list.name.orEmpty(),
                        implementationName = list.implementationName,
                        automaticAdd = list.enableAutomaticAdd,
                        searchForNew = list.shouldSearch,
                        monitor = list.shouldMonitor,
                        qualityProfileId = list.qualityProfileId,
                        metadataProfileId = list.metadataProfileId,
                        rootFolderPath = list.rootFolderPath,
                        minRefreshInterval = list.minRefreshInterval,
                    )
                },
                exclusionCount = exclusions,
            )
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<LidarrImportListsContent>> = state
        .map { s ->
            when {
                s.loadError != null && s.lists.isEmpty() -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.lists.isEmpty() -> LceState.Empty
                else -> LceState.Content(
                    LidarrImportListsContent(
                        lists = s.lists,
                        tags = s.tags,
                        queuedRemovedIds = s.queuedRemovedIds,
                        pendingActions = s.pendingActions,
                        preview = s.preview,
                    ),
                    refreshing = s.refreshing,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeImportLists().collect { lists ->
                _state.update {
                    it.copy(
                        loaded = true,
                        loadError = null,
                        lists = lists.sortedBy { list -> list.listOrder },
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.observeTags().collect { tags -> _state.update { it.copy(tags = tags) } }
        }
        viewModelScope.launch {
            repository.queuedRemovedIds(LidarrConnectRepository.IMPORT_LIST_PATH).collect { ids ->
                _state.update { it.copy(queuedRemovedIds = ids) }
            }
        }
        viewModelScope.launch {
            repository.queuedCount().collect { pending ->
                _state.update { it.copy(pendingActions = pending) }
            }
        }
        viewModelScope.launch {
            repository.observeExclusions().collect { exclusions ->
                _state.update { it.copy(exclusions = exclusions.size) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            when (val result = repository.refreshIfStale()) {
                is ArrResult.Ok -> _state.update { it.copy(refreshing = false) }

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
                    it.copy(testingAll = false, notice = ImportListsNotice.TEST_ALL_DONE)
                }

                is ArrResult.Err -> _state.update {
                    it.copy(testingAll = false, writeError = result.error)
                }
            }
        }
    }

    /** Starts the server-side ImportListSync command; never queues. */
    fun sync() {
        viewModelScope.launch {
            _state.update { it.copy(syncing = true) }
            when (val result = repository.syncImportLists()) {
                is ArrResult.Ok -> _state.update {
                    it.copy(syncing = false, notice = ImportListsNotice.SYNC_STARTED)
                }

                is ArrResult.Err -> _state.update {
                    it.copy(syncing = false, notice = ImportListsNotice.SYNC_FAILED)
                }
            }
        }
    }

    fun openPreview() {
        _state.update { it.copy(previewOpen = true) }
    }

    fun closePreview() {
        _state.update { it.copy(previewOpen = false) }
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

    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun consumeWriteError() {
        _state.update { it.copy(writeError = null) }
    }
}
