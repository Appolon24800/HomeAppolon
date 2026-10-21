package dev.appolon.homelab.arr.feature.lidarr.settings.connect.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.lidarr.model.DownloadClientConfigResource
import dev.appolon.homelab.arr.api.lidarr.model.DownloadClientResource
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

/** Content payload of the download clients LCE state. */
data class LidarrDownloadClientsContent(
    val clients: List<DownloadClientResource>,
    val tags: List<TagResource>,
    val queuedRemovedIds: Set<Int>,
    val pendingActions: Int,
)

/** Transient results of download client writes and probes, mapped to strings by the screen. */
enum class DownloadClientsNotice {
    DELETED,
    DELETED_QUEUED,
    TOGGLED_QUEUED,
    TEST_ALL_DONE,
    OPTIONS_SAVED,
    OPTIONS_SAVED_QUEUED,
}

/**
 * Download clients list state: rows with enable toggles, tag chips and
 * queued badges, test-all, the link to remote path mappings, and the
 * completed-download-handling options edited in place.
 */
class LidarrDownloadClientsViewModel(
    private val serverId: ServerId,
    private val repository: LidarrConnectRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val clients: List<DownloadClientResource> = emptyList(),
        val tags: List<TagResource> = emptyList(),
        val queuedRemovedIds: Set<Int> = emptySet(),
        val pendingActions: Int = 0,
        val options: DownloadClientConfigResource? = null,
        val testingAll: Boolean = false,
        val confirmDeleteFor: DownloadClientResource? = null,
        val writeError: ArrError? = null,
        val notice: DownloadClientsNotice? = null,
        val busy: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<LidarrDownloadClientsContent>> = state
        .map { s ->
            when {
                s.loadError != null && s.clients.isEmpty() -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.clients.isEmpty() -> LceState.Empty
                else -> LceState.Content(
                    LidarrDownloadClientsContent(
                        clients = s.clients,
                        tags = s.tags,
                        queuedRemovedIds = s.queuedRemovedIds,
                        pendingActions = s.pendingActions,
                    ),
                    refreshing = s.refreshing,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeDownloadClients().collect { clients ->
                _state.update {
                    it.copy(
                        loaded = true,
                        loadError = null,
                        clients = clients.sortedWith(
                            compareBy({ client -> client.priority }, { client -> client.name }),
                        ),
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.observeTags().collect { tags -> _state.update { it.copy(tags = tags) } }
        }
        viewModelScope.launch {
            repository.queuedRemovedIds(LidarrConnectRepository.DOWNLOAD_CLIENT_PATH).collect { ids ->
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
                    it.copy(
                        refreshing = false,
                        options = it.options ?: repository.cachedDownloadClientConfig(),
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(
                        loaded = true,
                        loadError = if (it.clients.isEmpty()) result.error else null,
                        writeError = if (it.clients.isEmpty()) null else result.error,
                        refreshing = false,
                    )
                }
            }
        }
    }

    /** Row enable toggle; a queued toggle stays visible through the optimistic row. */
    fun setEnabled(id: Int, enabled: Boolean) {
        viewModelScope.launch {
            when (val result = repository.setDownloadClientEnabled(id, enabled)) {
                is ArrResult.Ok -> if (result.value is WriteOutcome.Queued) {
                    _state.update { it.copy(notice = DownloadClientsNotice.TOGGLED_QUEUED) }
                }

                is ArrResult.Err -> _state.update { it.copy(writeError = result.error) }
            }
        }
    }

    fun testAll() {
        viewModelScope.launch {
            _state.update { it.copy(testingAll = true) }
            when (val result = repository.testAllDownloadClients()) {
                is ArrResult.Ok -> _state.update {
                    it.copy(testingAll = false, notice = DownloadClientsNotice.TEST_ALL_DONE)
                }

                is ArrResult.Err -> _state.update {
                    it.copy(testingAll = false, writeError = result.error)
                }
            }
        }
    }

    fun requestDelete(client: DownloadClientResource) {
        _state.update { it.copy(confirmDeleteFor = client) }
    }

    fun deleteCancelled() {
        _state.update { it.copy(confirmDeleteFor = null) }
    }

    fun deleteConfirmed() {
        val target = _state.value.confirmDeleteFor ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, confirmDeleteFor = null) }
            when (val result = repository.removeDownloadClient(target.id)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        busy = false,
                        notice = if (result.value is WriteOutcome.Queued) {
                            DownloadClientsNotice.DELETED_QUEUED
                        } else {
                            DownloadClientsNotice.DELETED
                        },
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(busy = false, writeError = result.error)
                }
            }
        }
    }

    fun updateOptions(config: DownloadClientConfigResource) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            when (val result = repository.saveDownloadClientConfig(config)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        busy = false,
                        options = config,
                        writeError = null,
                        notice = if (result.value is WriteOutcome.Queued) {
                            DownloadClientsNotice.OPTIONS_SAVED_QUEUED
                        } else {
                            DownloadClientsNotice.OPTIONS_SAVED
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
