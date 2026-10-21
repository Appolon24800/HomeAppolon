package dev.appolon.homelab.arr.feature.prowlarr.indexers.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.prowlarr.model.Field
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerProxyResource
import dev.appolon.homelab.arr.api.prowlarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.FieldForms
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.ProwlarrIndexersRepository
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.WriteOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

/** Transient one-line result of a proxy write, surfaced as a snackbar. */
sealed interface ProxiesMessage {
    data object Saved : ProxiesMessage
    data object SavedQueued : ProxiesMessage
    data object Deleted : ProxiesMessage
    data object DeleteQueued : ProxiesMessage
    data object TestPassed : ProxiesMessage
    data class TestFailed(val error: ArrError) : ProxiesMessage
    data class WriteFailed(val error: ArrError) : ProxiesMessage
    data object NameMissing : ProxiesMessage
}

/** Editor form of one indexer proxy, schema based when adding. */
data class ProxyEditorForm(
    val editing: IndexerProxyResource? = null,
    val schema: IndexerProxyResource? = null,
    val fields: List<Field> = emptyList(),
    val values: Map<String, JsonElement> = emptyMap(),
    val name: String = "",
    val tagLabels: List<String> = emptyList(),
    val nameMissing: Boolean = false,
)

/** State of the indexer proxies screen. */
data class ProxiesUiState(
    val loaded: Boolean = false,
    val loadError: ArrError? = null,
    val refreshing: Boolean = false,
    val proxies: List<IndexerProxyResource> = emptyList(),
    val tags: List<TagResource> = emptyList(),
    val editor: ProxyEditorForm? = null,
    val schemas: List<IndexerProxyResource> = emptyList(),
    val confirmDeleteId: Int? = null,
    val testing: Boolean = false,
    val saving: Boolean = false,
    val message: ProxiesMessage? = null,
) {
    fun tagLabels(proxy: IndexerProxyResource): List<String> =
        proxy.tags.orEmpty().mapNotNull { id -> tags.firstOrNull { it.id == id }?.label }
}

/**
 * ViewModel of the indexer proxies screen: list plus a schema driven CRUD
 * editor over the generic field model.
 */
class ProxiesViewModel(
    private val repository: ProwlarrIndexersRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProxiesUiState())
    val state: StateFlow<ProxiesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeProxies()
                .catch { }
                .collect { proxies -> _state.update { it.copy(proxies = proxies, loaded = true) } }
        }
        viewModelScope.launch {
            repository.observeTags()
                .catch { }
                .collect { tags -> _state.update { it.copy(tags = tags) } }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            when (val result = repository.refreshProxies()) {
                is ArrResult.Ok -> _state.update { it.copy(refreshing = false, loadError = null) }
                is ArrResult.Err ->
                    _state.update { it.copy(refreshing = false, loadError = result.error) }
            }
            when (val schemas = repository.proxySchemas()) {
                is ArrResult.Ok -> _state.update { it.copy(schemas = schemas.value) }
                is ArrResult.Err -> Unit
            }
        }
    }

    /** Opens the editor on the first proxy schema (Prowlarr ships one). */
    fun openAdd() {
        val schema = _state.value.schemas.firstOrNull() ?: return
        _state.update {
            it.copy(
                editor = ProxyEditorForm(
                    schema = schema,
                    fields = schema.fields.orEmpty(),
                    values = FieldForms.editableValues(schema.fields),
                ),
            )
        }
    }

    fun edit(proxy: IndexerProxyResource) {
        _state.update {
            it.copy(
                editor = ProxyEditorForm(
                    editing = proxy,
                    fields = proxy.fields.orEmpty(),
                    values = FieldForms.editableValues(proxy.fields),
                    name = proxy.name.orEmpty(),
                    tagLabels = it.tagLabels(proxy),
                ),
            )
        }
    }

    fun closeEditor() = _state.update { it.copy(editor = null) }

    fun setFieldValue(name: String, value: JsonElement) =
        _state.update { s ->
            val form = s.editor ?: return@update s
            s.copy(editor = form.copy(values = form.values + (name to value)))
        }

    fun setName(text: String) =
        _state.update { s ->
            val form = s.editor ?: return@update s
            s.copy(editor = form.copy(name = text, nameMissing = false))
        }

    fun toggleTagLabel(label: String) =
        _state.update { s ->
            val form = s.editor ?: return@update s
            val next = if (label in form.tagLabels) form.tagLabels - label else form.tagLabels + label
            s.copy(editor = form.copy(tagLabels = next))
        }

    fun save() {
        val form = _state.value.editor ?: return
        if (form.name.isBlank()) {
            updateForm { it.copy(nameMissing = true) }
            _state.update { it.copy(message = ProxiesMessage.NameMissing) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val tagIds = when (val result = repository.ensureTagIds(form.tagLabels)) {
                is ArrResult.Ok -> result.value
                is ArrResult.Err -> {
                    _state.update {
                        it.copy(saving = false, message = ProxiesMessage.WriteFailed(result.error))
                    }
                    return@launch
                }
            }
            val base = form.editing ?: form.schema ?: return@launch
            val resource = base.copy(
                fields = FieldForms.applyValues(base.fields, form.values),
                name = form.name.trim(),
                tags = tagIds,
            )
            when (val outcome = repository.saveProxy(resource)) {
                is ArrResult.Ok -> _state.update { s ->
                    s.copy(
                        saving = false,
                        editor = null,
                        message = if (outcome.value is WriteOutcome.Queued) {
                            ProxiesMessage.SavedQueued
                        } else {
                            ProxiesMessage.Saved
                        },
                    )
                }
                is ArrResult.Err -> _state.update {
                    it.copy(saving = false, message = ProxiesMessage.WriteFailed(outcome.error))
                }
            }
        }
    }

    fun test() {
        val form = _state.value.editor ?: return
        val base = form.editing ?: form.schema ?: return
        val resource = base.copy(
            fields = FieldForms.applyValues(base.fields, form.values),
            name = form.name.trim(),
        )
        _state.update { it.copy(testing = true) }
        viewModelScope.launch {
            when (val result = repository.testProxy(resource)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(testing = false, message = ProxiesMessage.TestPassed)
                }
                is ArrResult.Err -> _state.update {
                    it.copy(testing = false, message = ProxiesMessage.TestFailed(result.error))
                }
            }
        }
    }

    fun requestDelete(id: Int) = _state.update { it.copy(confirmDeleteId = id) }

    fun dismissDelete() = _state.update { it.copy(confirmDeleteId = null) }

    fun confirmDelete() {
        val id = _state.value.confirmDeleteId ?: return
        _state.update { it.copy(confirmDeleteId = null) }
        viewModelScope.launch {
            when (val outcome = repository.deleteProxy(id)) {
                is ArrResult.Ok -> _state.update { s ->
                    s.copy(
                        editor = null,
                        message = if (outcome.value is WriteOutcome.Queued) {
                            ProxiesMessage.DeleteQueued
                        } else {
                            ProxiesMessage.Deleted
                        },
                    )
                }
                is ArrResult.Err ->
                    _state.update { it.copy(message = ProxiesMessage.WriteFailed(outcome.error)) }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun updateForm(transform: (ProxyEditorForm) -> ProxyEditorForm) =
        _state.update { s ->
            val form = s.editor ?: return@update s
            s.copy(editor = transform(form))
        }
}
