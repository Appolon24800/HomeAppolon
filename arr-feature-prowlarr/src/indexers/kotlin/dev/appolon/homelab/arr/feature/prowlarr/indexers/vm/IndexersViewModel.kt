package dev.appolon.homelab.arr.feature.prowlarr.indexers.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.prowlarr.model.AppProfileResource
import dev.appolon.homelab.arr.api.prowlarr.model.ApplicationResource
import dev.appolon.homelab.arr.api.prowlarr.model.ApplyTags
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerBulkResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerStatusResource
import dev.appolon.homelab.arr.api.prowlarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.IndexerListQuery
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.IndexersPresentation
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.ProwlarrIndexersRepository
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.PrivacyFilter
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.ProtocolFilter
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.StateFilter
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.WriteOutcome
import dev.appolon.homelab.arr.net.arrError
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which layout the indexer list renders. */
enum class IndexersView { CARDS, TABLE }

/** Transient one-line result of a write or probe, surfaced as a snackbar. */
sealed interface IndexersMessage {
    data object ToggleSynced : IndexersMessage
    data object ToggleQueued : IndexersMessage
    data object BulkSynced : IndexersMessage
    data object BulkQueued : IndexersMessage
    data object DeleteSynced : IndexersMessage
    data object DeleteQueued : IndexersMessage
    data object TestPassed : IndexersMessage
    data class TestFailed(val error: ArrError) : IndexersMessage
    data object TestAllStarted : IndexersMessage
    data class TestAllFailed(val error: ArrError) : IndexersMessage
    data class WriteFailed(val error: ArrError) : IndexersMessage
}

/** Bulk editor form; null fields mean leave unchanged. */
data class IndexerBulkForm(
    val enable: Boolean? = null,
    val appProfileId: Int? = null,
    val priority: Int? = null,
    val tagLabels: List<String> = emptyList(),
    val applyTags: ApplyTags = ApplyTags.ADD,
)

/**
 * State of the indexers screen. Everything the composables render is here;
 * the filtered presentation derives through [IndexersPresentation] so it
 * stays testable without Android types.
 */
data class IndexersUiState(
    val loaded: Boolean = false,
    val loadError: ArrError? = null,
    val indexers: List<IndexerResource> = emptyList(),
    val statuses: Map<Int, IndexerStatusResource> = emptyMap(),
    val tags: List<TagResource> = emptyList(),
    val appProfiles: List<AppProfileResource> = emptyList(),
    val applications: List<ApplicationResource> = emptyList(),
    val query: IndexerListQuery = IndexerListQuery(),
    val view: IndexersView = IndexersView.CARDS,
    val selectMode: Boolean = false,
    val selection: Set<Int> = emptySet(),
    val queuedForServer: Int = 0,
    val queuedIndexerIds: Set<Int> = emptySet(),
    val refreshing: Boolean = false,
    val bulkForm: IndexerBulkForm? = null,
    val confirmDelete: Boolean = false,
    val message: IndexersMessage? = null,
) {

    /** The filtered, sorted presentation the cards and table render. */
    val presentation: List<IndexerResource> by lazy(LazyThreadSafetyMode.NONE) {
        IndexersPresentation.present(indexers, statuses, query)
    }

    /** Tag labels for one indexer, resolved against the tag cache. */
    fun tagLabels(indexer: IndexerResource): List<String> =
        indexer.tags.orEmpty().mapNotNull { id -> tags.firstOrNull { it.id == id }?.label }

    /** Label of the app profile, falling back to the raw id. */
    fun appProfileLabel(id: Int): String =
        appProfiles.firstOrNull { it.id == id }?.name ?: "#$id"

    /** ids the next bulk operation targets: the selection, or everything presented. */
    val bulkTargets: List<Int>
        get() = if (selectMode && selection.isNotEmpty()) {
            selection.sorted()
        } else {
            presentation.map { it.id }
        }

    /** True when any application syncs indexers into a target app. */
    val anyApplicationSyncing: Boolean
        get() = applications.any {
            it.syncLevel != dev.appolon.homelab.arr.api.prowlarr.model.ApplicationSyncLevel.DISABLED
        }
}

/**
 * ViewModel of the Prowlarr indexers list: filters, card and table views,
 * single toggles, bulk edit with ApplyTags semantics, deletes, and probes.
 * Owns no Android types so unit tests construct it against fakes.
 */
class IndexersViewModel(
    private val repository: ProwlarrIndexersRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(IndexersUiState())
    val state: StateFlow<IndexersUiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<IndexersUiState>> = state
        .map { s ->
            when {
                s.loadError != null && s.indexers.isEmpty() -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                s.indexers.isEmpty() -> LceState.Empty
                else -> LceState.Content(s, refreshing = s.refreshing)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeIndexers()
                .catch { t -> _state.update { it.copy(loaded = true, loadError = t.arrError()) } }
                .collect { indexers ->
                    _state.update { it.copy(indexers = indexers, loaded = true) }
                }
        }
        viewModelScope.launch {
            repository.observeStatuses()
                .catch { }
                .collect { statuses ->
                    _state.update { it.copy(statuses = statuses.associateBy { s -> s.indexerId }) }
                }
        }
        viewModelScope.launch {
            repository.observeTags()
                .catch { }
                .collect { tags -> _state.update { it.copy(tags = tags) } }
        }
        viewModelScope.launch {
            repository.observeApplications()
                .catch { }
                .collect { apps -> _state.update { it.copy(applications = apps) } }
        }
        viewModelScope.launch {
            repository.queuedCount()
                .catch { }
                .collect { count -> _state.update { it.copy(queuedForServer = count) } }
        }
        viewModelScope.launch {
            repository.queuedIndexerIds()
                .catch { }
                .collect { ids -> _state.update { it.copy(queuedIndexerIds = ids) } }
        }
        viewModelScope.launch { refreshReferenceData() }
        refresh()
    }

    /** Pull-to-refresh and error retry entry point. */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            when (val result = repository.refresh()) {
                is ArrResult.Ok -> _state.update { it.copy(refreshing = false, loadError = null) }
                is ArrResult.Err ->
                    _state.update { it.copy(refreshing = false, loadError = result.error) }
            }
        }
    }

    fun setView(view: IndexersView) = _state.update { it.copy(view = view) }

    fun setSort(sortId: String) = _state.update { it.copy(query = it.query.copy(sortId = sortId)) }

    fun toggleSortDescending() =
        _state.update { it.copy(query = it.query.copy(sortDescending = !it.query.sortDescending)) }

    fun setSearch(text: String) =
        _state.update { it.copy(query = it.query.copy(search = text)) }

    fun setProtocolFilter(filter: ProtocolFilter) =
        _state.update { it.copy(query = it.query.copy(protocol = filter)) }

    fun setPrivacyFilter(filter: PrivacyFilter) =
        _state.update { it.copy(query = it.query.copy(privacy = filter)) }

    fun setStateFilter(filter: StateFilter) =
        _state.update { it.copy(query = it.query.copy(state = filter)) }

    fun setTagFilter(tagId: Int?) =
        _state.update { it.copy(query = it.query.copy(tagId = tagId)) }

    fun clearFilters() = _state.update { it.copy(query = IndexerListQuery()) }

    fun enterSelectMode(first: Int) =
        _state.update { it.copy(selectMode = true, selection = setOf(first)) }

    fun exitSelectMode() =
        _state.update { it.copy(selectMode = false, selection = emptySet()) }

    fun toggleSelected(indexerId: Int) = _state.update { s ->
        val next = if (indexerId in s.selection) s.selection - indexerId else s.selection + indexerId
        s.copy(selection = next, selectMode = next.isNotEmpty() || s.selectMode)
    }

    fun selectAllPresented() =
        _state.update { s ->
            s.copy(selection = s.presentation.map { it.id }.toSet(), selectMode = true)
        }

    /** Enabled toggle on one card or table row. */
    fun setEnabled(indexerId: Int, enabled: Boolean) {
        viewModelScope.launch {
            when (val outcome = repository.setEnabled(indexerId, enabled)) {
                is ArrResult.Ok -> _state.update { s ->
                    s.copy(
                        message = if (outcome.value is WriteOutcome.Queued) {
                            IndexersMessage.ToggleQueued
                        } else {
                            IndexersMessage.ToggleSynced
                        },
                    )
                }
                is ArrResult.Err ->
                    _state.update { it.copy(message = IndexersMessage.WriteFailed(outcome.error)) }
            }
        }
    }

    fun openBulkEditor() = _state.update { it.copy(bulkForm = IndexerBulkForm()) }

    fun closeBulkEditor() = _state.update { it.copy(bulkForm = null) }

    fun updateBulkEditor(transform: (IndexerBulkForm) -> IndexerBulkForm) =
        _state.update { s ->
            val form = s.bulkForm ?: return@update s
            s.copy(bulkForm = transform(form))
        }

    /** Applies the bulk form to the current targets. */
    fun submitBulkEditor() {
        val form = _state.value.bulkForm ?: return
        viewModelScope.launch {
            val tagIds = when (val result = repository.ensureTagIds(form.tagLabels)) {
                is ArrResult.Ok -> result.value
                is ArrResult.Err -> {
                    _state.update { it.copy(message = IndexersMessage.WriteFailed(result.error)) }
                    return@launch
                }
            }
            val tagsForApply =
                tagIds.takeIf { it.isNotEmpty() || form.applyTags == ApplyTags.REPLACE }
            val bulk = IndexerBulkResource(
                ids = _state.value.bulkTargets,
                tags = tagsForApply,
                applyTags = if (tagsForApply != null) form.applyTags else ApplyTags.ADD,
                enable = form.enable,
                appProfileId = form.appProfileId,
                priority = form.priority,
            )
            when (val outcome = repository.bulkEdit(bulk)) {
                is ArrResult.Ok -> _state.update { s ->
                    s.copy(
                        bulkForm = null,
                        message = if (outcome.value is WriteOutcome.Queued) {
                            IndexersMessage.BulkQueued
                        } else {
                            IndexersMessage.BulkSynced
                        },
                    )
                }
                is ArrResult.Err ->
                    _state.update { it.copy(message = IndexersMessage.WriteFailed(outcome.error)) }
            }
        }
    }

    fun requestDelete() = _state.update { it.copy(confirmDelete = true) }

    fun dismissDelete() = _state.update { it.copy(confirmDelete = false) }

    /** Deletes the current targets, selection first. */
    fun confirmDelete() {
        val targets = _state.value.bulkTargets
        _state.update { it.copy(confirmDelete = false) }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            when (val outcome = repository.deleteIndexers(targets)) {
                is ArrResult.Ok -> _state.update { s ->
                    s.copy(
                        selectMode = false,
                        selection = emptySet(),
                        message = if (outcome.value is WriteOutcome.Queued) {
                            IndexersMessage.DeleteQueued
                        } else {
                            IndexersMessage.DeleteSynced
                        },
                    )
                }
                is ArrResult.Err ->
                    _state.update { it.copy(message = IndexersMessage.WriteFailed(outcome.error)) }
            }
        }
    }

    /** Tests one indexer against its cached configuration. */
    fun testIndexer(indexerId: Int) {
        viewModelScope.launch {
            when (val found = repository.indexer(indexerId)) {
                is ArrResult.Ok -> when (val result = repository.testIndexer(found.value)) {
                    is ArrResult.Ok -> _state.update { it.copy(message = IndexersMessage.TestPassed) }
                    is ArrResult.Err -> _state.update {
                        it.copy(message = IndexersMessage.TestFailed(result.error))
                    }
                }
                is ArrResult.Err ->
                    _state.update { it.copy(message = IndexersMessage.WriteFailed(found.error)) }
            }
        }
    }

    fun testAll() {
        viewModelScope.launch {
            when (val result = repository.testAllIndexers()) {
                is ArrResult.Ok ->
                    _state.update { it.copy(message = IndexersMessage.TestAllStarted) }
                is ArrResult.Err -> _state.update {
                    it.copy(message = IndexersMessage.TestAllFailed(result.error))
                }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private suspend fun refreshReferenceData() {
        repository.appProfiles().let { result ->
            if (result is ArrResult.Ok) {
                _state.update { it.copy(appProfiles = result.value) }
            }
        }
    }
}
