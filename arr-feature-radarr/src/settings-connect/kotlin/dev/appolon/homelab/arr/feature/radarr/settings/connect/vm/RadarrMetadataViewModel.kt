package dev.appolon.homelab.arr.feature.radarr.settings.connect.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.radarr.model.Field
import dev.appolon.homelab.arr.api.radarr.model.MetadataResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.radarr.settings.connect.data.ConnectFieldForms
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
import kotlinx.serialization.json.JsonElement

/** Content payload of the metadata LCE state: rows plus expanded drafts. */
data class RadarrMetadataContent(
    val consumers: List<MetadataResource>,
    val drafts: Map<Int, MetadataDraft>,
    val queuedRemovedIds: Set<Int>,
    val pendingActions: Int,
)

/** Editable metadata consumer draft for the inline expandable editors. */
data class MetadataDraft(
    val id: Int = 0,
    val name: String = "",
    val enable: Boolean = false,
    val fields: List<Field> = emptyList(),
    val values: Map<String, JsonElement> = emptyMap(),
    val implementation: String? = null,
    val implementationName: String? = null,
    val infoLink: String? = null,
)

/** Transient results of metadata actions, mapped to strings by the screen. */
enum class MetadataNotice {
    SAVED,
    SAVED_QUEUED,
    TEST_PASSED,
}

/**
 * Metadata consumers state. Radarr ships every consumer as a schema row,
 * so the screen lists them with inline expandable editors (enable toggle
 * plus the dynamic fields); saving writes the row back, and test probes
 * the current draft. Delete exists for rows the server allows removing.
 */
class RadarrMetadataViewModel(
    private val serverId: ServerId,
    private val repository: RadarrConnectRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val consumers: List<MetadataResource> = emptyList(),
        val drafts: Map<Int, MetadataDraft> = emptyMap(),
        val queuedRemovedIds: Set<Int> = emptySet(),
        val pendingActions: Int = 0,
        val testingId: Int? = null,
        val writeError: ArrError? = null,
        val testError: ArrError? = null,
        val notice: MetadataNotice? = null,
        val busy: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<RadarrMetadataContent>> = state
        .map { s ->
            when {
                s.loadError != null && s.consumers.isEmpty() -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.consumers.isEmpty() -> LceState.Empty
                else -> LceState.Content(
                    RadarrMetadataContent(s.consumers, s.drafts, s.queuedRemovedIds, s.pendingActions),
                    refreshing = s.refreshing,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeMetadata().collect { consumers ->
                _state.update { state ->
                    state.copy(
                        loaded = true,
                        loadError = null,
                        consumers = consumers,
                        drafts = consumers.associate { it.id to draftOf(it) },
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.queuedRemovedIds(RadarrConnectRepository.METADATA_PATH).collect { ids ->
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

    private fun draftOf(resource: MetadataResource): MetadataDraft = MetadataDraft(
        id = resource.id,
        name = resource.name.orEmpty(),
        enable = resource.enable,
        fields = resource.fields.orEmpty(),
        values = ConnectFieldForms.editableValues(resource.fields),
        implementation = resource.implementation,
        implementationName = resource.implementationName,
        infoLink = resource.infoLink,
    )

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

    fun updateDraft(id: Int, transform: (MetadataDraft) -> MetadataDraft) {
        _state.update { s ->
            val current = s.drafts[id] ?: return@update s
            s.copy(drafts = s.drafts + (id to transform(current)))
        }
    }

    fun onValueChange(id: Int, name: String, value: JsonElement) {
        updateDraft(id) { it.copy(values = it.values + (name to value)) }
    }

    fun setEnabled(id: Int, enabled: Boolean) {
        updateDraft(id) { it.copy(enable = enabled) }
        save(id)
    }

    private fun resourceOf(draft: MetadataDraft): MetadataResource = MetadataResource(
        id = draft.id,
        name = draft.name,
        fields = ConnectFieldForms.applyValues(draft.fields, draft.values),
        implementation = draft.implementation,
        implementationName = draft.implementationName,
        infoLink = draft.infoLink,
        enable = draft.enable,
    )

    fun save(id: Int) {
        val draft = _state.value.drafts[id] ?: return
        if (draft.name.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            when (val result = repository.saveMetadata(resourceOf(draft))) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        busy = false,
                        writeError = null,
                        notice = if (result.value is WriteOutcome.Queued) {
                            MetadataNotice.SAVED_QUEUED
                        } else {
                            MetadataNotice.SAVED
                        },
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(busy = false, writeError = result.error)
                }
            }
        }
    }

    fun test(id: Int) {
        val draft = _state.value.drafts[id] ?: return
        viewModelScope.launch {
            _state.update { it.copy(testingId = id, testError = null) }
            when (val result = repository.testMetadata(resourceOf(draft))) {
                is ArrResult.Ok -> _state.update {
                    it.copy(testingId = null, notice = MetadataNotice.TEST_PASSED)
                }

                is ArrResult.Err -> _state.update {
                    it.copy(testingId = null, testError = result.error)
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

    fun consumeTestError() {
        _state.update { it.copy(testError = null) }
    }
}
