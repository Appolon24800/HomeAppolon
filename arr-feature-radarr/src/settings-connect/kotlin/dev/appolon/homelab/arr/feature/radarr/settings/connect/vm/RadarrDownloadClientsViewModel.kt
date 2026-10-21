package dev.appolon.homelab.arr.feature.radarr.settings.connect.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.radarr.model.DownloadClientConfigResource
import dev.appolon.homelab.arr.api.radarr.model.DownloadClientResource
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

/** Content payload of the download clients LCE state. */
data class RadarrDownloadClientsContent(
    val clients: List<DownloadClientResource>,
    val tags: List<TagResource>,
    val queuedRemovedIds: Set<Int>,
    val pendingActions: Int,
    val testResults: Map<Int, ProviderTestAllResult>,
)

/** Transient results of download client actions, mapped to strings by the screen. */
enum class DownloadClientsNotice {
    DELETED,
    DELETED_QUEUED,
    TOGGLE_QUEUED,
    TEST_ALL_DONE,
    OPTIONS_SAVED,
    OPTIONS_SAVED_QUEUED,
}

/**
 * Download clients list state: cached rows with enable toggles, test-all
 * with per-client verdicts, queued badges for pending removals, and the
 * download client options config (completed download handling) edited in
 * place.
 */
class RadarrDownloadClientsViewModel(
    private val serverId: ServerId,
    private val repository: RadarrConnectRepository,
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
        val testResults: Map<Int, ProviderTestAllResult> = emptyMap(),
        val testingAll: Boolean = false,
        val confirmDeleteFor: DownloadClientResource? = null,
        val writeError: ArrError? = null,
        val notice: DownloadClientsNotice? = null,
        val busy: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<RadarrDownloadClientsContent>> = state
        .map { s ->
            when {
                s.loadError != null && s.clients.isEmpty() -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.clients.isEmpty() -> LceState.Empty
                else -> LceState.Content(
                    RadarrDownloadClientsContent(
                        clients = s.clients,
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
            repository.observeDownloadClients().collect { clients ->
                _state.update {
                    it.copy(loaded = true, loadError = null, clients = clients.sortedBy { c -> c.priority })
                }
            }
        }
        viewModelScope.launch {
            repository.observeTags().collect { tags -> _state.update { it.copy(tags = tags) } }
        }
        viewModelScope.launch {
            repository.queuedRemovedIds(RadarrConnectRepository.DOWNLOAD_CLIENT_PATH).collect { ids ->
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

    /** Row enable toggle; a failed online toggle still queues optimistically. */
    fun setEnabled(client: DownloadClientResource, enabled: Boolean) {
        viewModelScope.launch {
            when (val result = repository.setDownloadClientEnabled(client.id, enabled)) {
                is ArrResult.Ok -> if (result.value is WriteOutcome.Queued) {
                    _state.update { it.copy(notice = DownloadClientsNotice.TOGGLE_QUEUED) }
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
                    it.copy(
                        testingAll = false,
                        testResults = result.value.associateBy { verdict -> verdict.id },
                        notice = DownloadClientsNotice.TEST_ALL_DONE,
                    )
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
