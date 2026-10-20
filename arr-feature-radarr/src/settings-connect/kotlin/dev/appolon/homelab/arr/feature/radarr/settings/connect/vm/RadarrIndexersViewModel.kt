package dev.appolon.homelab.arr.feature.radarr.settings.connect.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.radarr.model.IndexerConfigResource
import dev.appolon.homelab.arr.api.radarr.model.IndexerResource
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

/** Content payload of the indexers LCE state. */
data class RadarrIndexersContent(
    val indexers: List<IndexerResource>,
    val tags: List<TagResource>,
    val queuedRemovedIds: Set<Int>,
    val pendingActions: Int,
    val testResults: Map<Int, ProviderTestAllResult>,
)

/** Transient results of indexer writes and probes, mapped to strings by the screen. */
enum class IndexersNotice {
    DELETED,
    DELETED_QUEUED,
    TEST_ALL_DONE,
    OPTIONS_SAVED,
    OPTIONS_SAVED_QUEUED,
}

/**
 * Indexers list state: the cached rows with tag chips and queued badges,
 * test-all with per-indexer verdicts, and the indexer options config
 * (minimum age, retention, RSS interval) edited in place.
 */
class RadarrIndexersViewModel(
    private val serverId: ServerId,
    private val repository: RadarrConnectRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val indexers: List<IndexerResource> = emptyList(),
        val tags: List<TagResource> = emptyList(),
        val queuedRemovedIds: Set<Int> = emptySet(),
        val pendingActions: Int = 0,
        val options: IndexerConfigResource? = null,
        val testResults: Map<Int, ProviderTestAllResult> = emptyMap(),
        val testingAll: Boolean = false,
        val confirmDeleteFor: IndexerResource? = null,
        val writeError: ArrError? = null,
        val notice: IndexersNotice? = null,
        val busy: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<RadarrIndexersContent>> = state
        .map { s ->
            when {
                s.loadError != null && s.indexers.isEmpty() -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.indexers.isEmpty() -> LceState.Empty
                else -> LceState.Content(
                    RadarrIndexersContent(
                        indexers = s.indexers,
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
            repository.observeIndexers().collect { indexers ->
                _state.update {
                    it.copy(
                        loaded = true,
                        loadError = null,
                        indexers = indexers.sortedBy { indexer -> indexer.priority },
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.observeTags().collect { tags -> _state.update { it.copy(tags = tags) } }
        }
        viewModelScope.launch {
            repository.queuedRemovedIds(RadarrConnectRepository.INDEXER_PATH).collect { ids ->
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
                    it.copy(refreshing = false, options = it.options ?: repository.cachedIndexerConfig())
                }

                is ArrResult.Err -> _state.update {
                    it.copy(
                        loaded = true,
                        loadError = if (it.indexers.isEmpty()) result.error else null,
                        writeError = if (it.indexers.isEmpty()) null else result.error,
                        refreshing = false,
                    )
                }
            }
        }
    }

    /** Runs testall and keeps the per-indexer verdicts for row badges. */
    fun testAll() {
        viewModelScope.launch {
            _state.update { it.copy(testingAll = true) }
            when (val result = repository.testAllIndexers()) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        testingAll = false,
                        testResults = result.value.associateBy { verdict -> verdict.id },
                        notice = IndexersNotice.TEST_ALL_DONE,
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(testingAll = false, writeError = result.error)
                }
            }
        }
    }

    fun requestDelete(indexer: IndexerResource) {
        _state.update { it.copy(confirmDeleteFor = indexer) }
    }

    fun deleteCancelled() {
        _state.update { it.copy(confirmDeleteFor = null) }
    }

    fun deleteConfirmed() {
        val target = _state.value.confirmDeleteFor ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, confirmDeleteFor = null) }
            applyWrite(repository.removeIndexer(target.id)) { queued ->
                if (queued) IndexersNotice.DELETED_QUEUED else IndexersNotice.DELETED
            }
        }
    }

    fun updateOptions(config: IndexerConfigResource) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            when (val result = repository.saveIndexerConfig(config)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        busy = false,
                        options = config,
                        writeError = null,
                        notice = if (result.value is WriteOutcome.Queued) {
                            IndexersNotice.OPTIONS_SAVED_QUEUED
                        } else {
                            IndexersNotice.OPTIONS_SAVED
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

    private fun applyWrite(
        result: ArrResult<WriteOutcome>,
        notice: (queued: Boolean) -> IndexersNotice,
    ) {
        when (result) {
            is ArrResult.Ok -> _state.update {
                it.copy(busy = false, notice = notice(result.value is WriteOutcome.Queued), writeError = null)
            }

            is ArrResult.Err -> _state.update {
                it.copy(busy = false, notice = null, writeError = result.error)
            }
        }
    }
}
