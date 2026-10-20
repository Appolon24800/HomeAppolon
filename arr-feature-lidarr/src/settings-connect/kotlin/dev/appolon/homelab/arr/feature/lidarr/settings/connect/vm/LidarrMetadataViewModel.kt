package dev.appolon.homelab.arr.feature.lidarr.settings.connect.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.lidarr.model.Field
import dev.appolon.homelab.arr.api.lidarr.model.MetadataResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.data.LidarrConnectFieldForms
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
import kotlinx.serialization.json.JsonElement

/** Per-consumer editable state: the descriptor list plus edited values. */
data class MetadataConsumerDraft(
    val id: Int = 0,
    val name: String = "",
    val enable: Boolean = false,
    val implementationName: String? = null,
    val fields: List<Field> = emptyList(),
    val values: Map<String, JsonElement> = emptyMap(),
)

/** Transient results of metadata writes, mapped to strings by the screen. */
enum class MetadataNotice { SAVED, SAVED_QUEUED, TEST_PASSED }

/**
 * Metadata consumers state: rows with enable toggles that expand into the
 * provider's dynamic field form. Saves write through with the offline
 * queue fallback; test sends a probe with the current values.
 */
class LidarrMetadataViewModel(
    private val serverId: ServerId,
    private val repository: LidarrConnectRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val consumers: List<MetadataResource> = emptyList(),
        /** Drafts keyed by consumer id; a key present means its form is open. */
        val drafts: Map<Int, MetadataConsumerDraft> = emptyMap(),
        val testingIds: Set<Int> = emptySet(),
        val writeError: ArrError? = null,
        val notice: MetadataNotice? = null,
        val busy: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<UiState>> = state
        .map { s ->
            when {
                s.loadError != null && s.consumers.isEmpty() -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.consumers.isEmpty() -> LceState.Empty
                else -> LceState.Content(s, refreshing = s.refreshing)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeMetadata().collect { consumers ->
                _state.update {
                    it.copy(
                        loaded = true,
                        loadError = null,
                        consumers = consumers.sortedBy { consumer -> consumer.name },
                    )
                }
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
                        loadError = if (it.consumers.isEmpty()) result.error else null,
                        writeError = if (it.consumers.isEmpty()) null else result.error,
                        refreshing = false,
                    )
                }
            }
        }
    }

    fun openForm(consumer: MetadataResource) {
        val draft = MetadataConsumerDraft(
            id = consumer.id,
            name = consumer.name.orEmpty(),
            enable = consumer.enable,
            implementationName = consumer.implementationName,
            fields = consumer.fields.orEmpty(),
            values = LidarrConnectFieldForms.editableValues(consumer.fields),
        )
        _state.update { state -> state.copy(drafts = state.drafts + (consumer.id to draft)) }
    }

    fun closeForm(id: Int) {
        _state.update { state -> state.copy(drafts = state.drafts - id) }
    }

    fun updateDraft(id: Int, transform: (MetadataConsumerDraft) -> MetadataConsumerDraft) {
        _state.update { state ->
            state.copy(drafts = state.drafts + (id to state.drafts.getValue(id).let(transform)))
        }
    }

    fun onValueChange(id: Int, name: String, value: JsonElement) {
        updateDraft(id) { it.copy(values = it.values + (name to value)) }
    }

    fun setEnabled(id: Int, enabled: Boolean) {
        viewModelScope.launch {
            when (val result = repository.setMetadataEnabled(id, enabled)) {
                is ArrResult.Ok -> if (result.value is WriteOutcome.Queued) {
                    _state.update {
                        it.copy(notice = MetadataNotice.SAVED_QUEUED)
                    }
                }

                is ArrResult.Err -> _state.update { it.copy(writeError = result.error) }
            }
        }
    }

    fun save(id: Int) {
        val draft = _state.value.drafts[id] ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val saved = repository.saveMetadata(
                MetadataResource(
                    id = draft.id,
                    name = draft.name,
                    enable = draft.enable,
                    implementationName = draft.implementationName,
                    fields = LidarrConnectFieldForms.applyValues(draft.fields, draft.values),
                ),
            )
            when (saved) {
                is ArrResult.Ok -> _state.update { state ->
                    state.copy(
                        busy = false,
                        drafts = state.drafts - id,
                        writeError = null,
                        notice = if (saved.value is WriteOutcome.Queued) {
                            MetadataNotice.SAVED_QUEUED
                        } else {
                            MetadataNotice.SAVED
                        },
                    )
                }

                is ArrResult.Err -> _state.update { it.copy(busy = false, writeError = saved.error) }
            }
        }
    }

    fun test(id: Int) {
        val draft = _state.value.drafts[id] ?: return
        viewModelScope.launch {
            _state.update { it.copy(testingIds = it.testingIds + id) }
            val result = repository.testMetadata(
                MetadataResource(
                    id = draft.id,
                    name = draft.name,
                    enable = draft.enable,
                    implementationName = draft.implementationName,
                    fields = LidarrConnectFieldForms.applyValues(draft.fields, draft.values),
                ),
            )
            when (result) {
                is ArrResult.Ok -> _state.update {
                    it.copy(testingIds = it.testingIds - id, notice = MetadataNotice.TEST_PASSED)
                }

                is ArrResult.Err -> _state.update {
                    it.copy(testingIds = it.testingIds - id, writeError = result.error)
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
