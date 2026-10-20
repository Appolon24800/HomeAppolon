package dev.appolon.homelab.arr.feature.radarr.addnew.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.radarr.model.CollectionResource
import dev.appolon.homelab.arr.api.radarr.model.QualityProfileResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.radarr.addnew.data.AddOutcome
import dev.appolon.homelab.arr.feature.radarr.addnew.data.RadarrAddNewRepository
import dev.appolon.homelab.arr.feature.radarr.addnew.data.RadarrCollectionsRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Transient one-line result of a collection write, surfaced as a snackbar. */
sealed interface CollectionsMessage {
    data class Updated(val title: String) : CollectionsMessage
    data class UpdateQueued(val title: String) : CollectionsMessage
    data class UpdateFailed(val title: String) : CollectionsMessage
    data class SearchStarted(val count: Int, val title: String) : CollectionsMessage
    data class SearchQueued(val title: String) : CollectionsMessage
    data class SearchFailed(val title: String) : CollectionsMessage
    data class NothingToSearch(val title: String) : CollectionsMessage
}

/** Form state of the collection edit sheet. */
data class CollectionEditUiState(
    val collection: CollectionResource,
    val monitored: Boolean,
    val qualityProfileId: Int,
    val searchOnAdd: Boolean,
    val submitting: Boolean = false,
)

/** State of the Collections tab of the Add new screen. */
data class CollectionsUiState(
    val loaded: Boolean = false,
    val loadError: ArrError? = null,
    val collections: List<CollectionResource> = emptyList(),
    val qualityProfiles: List<QualityProfileResource> = emptyList(),
    val queuedCollectionIds: Set<Int> = emptySet(),
    val searchingIds: Set<Int> = emptySet(),
    val refreshing: Boolean = false,
    val editSheet: CollectionEditUiState? = null,
    val message: CollectionsMessage? = null,
) {
    /** LCE for the collections list. */
    val lce: LceState<List<CollectionResource>>
        get() = when {
            !loaded && loadError == null -> LceState.Loading
            loadError != null && collections.isEmpty() -> LceState.Error(loadError)
            collections.isEmpty() -> LceState.Empty
            else -> LceState.Content(collections, refreshing = refreshing)
        }

    /** Label of the quality profile, falling back to the raw id. */
    fun profileLabel(id: Int): String =
        qualityProfiles.firstOrNull { it.id == id }?.name ?: "#$id"
}

/**
 * ViewModel of the Collections browse tab: list with monitoring toggles, the
 * edit sheet (monitoring, quality profile, search on add), and the
 * search-missing action. Owns no Android types so unit tests construct it
 * directly against fakes; the add-option catalogs come from the shared
 * add-new repository so both tabs show one consistent catalog.
 */
class RadarrCollectionsViewModel(
    private val repository: RadarrCollectionsRepository,
    addNewRepository: RadarrAddNewRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CollectionsUiState())
    val state: StateFlow<CollectionsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeCollections()
                .catch { }
                .collect { collections ->
                    _state.update { it.copy(collections = collections, loaded = true) }
                }
        }
        viewModelScope.launch {
            repository.queuedCollectionIds()
                .catch { }
                .collect { ids -> _state.update { it.copy(queuedCollectionIds = ids) } }
        }
        viewModelScope.launch { repository.refreshIfStale() }
        viewModelScope.launch {
            when (val result = addNewRepository.qualityProfiles()) {
                is ArrResult.Ok -> _state.update { it.copy(qualityProfiles = result.value) }
                is ArrResult.Err -> Unit
            }
        }
    }

    /** Pull-to-refresh for the collections tab. */
    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            when (val result = repository.refresh()) {
                is ArrResult.Ok -> _state.update {
                    it.copy(loaded = true, loadError = null, refreshing = false)
                }

                is ArrResult.Err -> _state.update {
                    it.copy(loaded = true, loadError = result.error, refreshing = false)
                }
            }
        }
    }

    /** Quick monitoring toggle straight from the row. */
    fun toggleMonitored(collection: CollectionResource) {
        viewModelScope.launch {
            applyUpdate(collection.copy(monitored = !collection.monitored))
        }
    }

    fun openEditor(collection: CollectionResource) {
        _state.update {
            it.copy(
                editSheet = CollectionEditUiState(
                    collection = collection,
                    monitored = collection.monitored,
                    qualityProfileId = collection.qualityProfileId,
                    searchOnAdd = collection.searchOnAdd,
                ),
            )
        }
    }

    fun updateEditor(transform: (CollectionEditUiState) -> CollectionEditUiState) {
        _state.update { state ->
            state.editSheet?.let { sheet -> state.copy(editSheet = transform(sheet)) } ?: state
        }
    }

    fun dismissEditor() = _state.update { it.copy(editSheet = null) }

    /** Saves the edit sheet through PUT /api/v3/collection/{id}. */
    fun submitEditor() {
        val sheet = _state.value.editSheet ?: return
        if (sheet.submitting) return
        updateEditor { it.copy(submitting = true) }
        viewModelScope.launch {
            applyUpdate(
                sheet.collection.copy(
                    monitored = sheet.monitored,
                    qualityProfileId = sheet.qualityProfileId,
                    searchOnAdd = sheet.searchOnAdd,
                ),
            )
            _state.update { it.copy(editSheet = null) }
        }
    }

    /** Searches the collection's missing movies with the MoviesSearch command. */
    fun searchMissing(collection: CollectionResource) {
        if (collection.id in _state.value.searchingIds) return
        _state.update { it.copy(searchingIds = it.searchingIds + collection.id) }
        viewModelScope.launch {
            val title = collection.title.orEmpty().ifEmpty { collection.tmdbId.toString() }
            val message = when (val missing = repository.missingMovieIds(collection.tmdbId)) {
                is ArrResult.Err -> CollectionsMessage.SearchFailed(title)

                is ArrResult.Ok -> {
                    if (missing.value.isEmpty()) {
                        CollectionsMessage.NothingToSearch(title)
                    } else {
                        when (val outcome = repository.searchMissing(missing.value)) {
                            is ArrResult.Ok -> when (outcome.value) {
                                AddOutcome.Synced ->
                                    CollectionsMessage.SearchStarted(missing.value.size, title)

                                is AddOutcome.Queued -> CollectionsMessage.SearchQueued(title)
                            }

                            is ArrResult.Err -> CollectionsMessage.SearchFailed(title)
                        }
                    }
                }
            }
            _state.update {
                it.copy(message = message, searchingIds = it.searchingIds - collection.id)
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private suspend fun applyUpdate(updated: CollectionResource) {
        val title = updated.title.orEmpty().ifEmpty { updated.tmdbId.toString() }
        when (val result = repository.updateCollection(updated)) {
            is ArrResult.Ok -> {
                val message = when (result.value) {
                    AddOutcome.Synced -> CollectionsMessage.Updated(title)
                    is AddOutcome.Queued -> CollectionsMessage.UpdateQueued(title)
                }
                _state.update { it.copy(message = message) }
            }

            is ArrResult.Err -> _state.update {
                it.copy(message = CollectionsMessage.UpdateFailed(title))
            }
        }
    }
}
