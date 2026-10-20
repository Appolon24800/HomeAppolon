package dev.appolon.homelab.arr.feature.prowlarr.indexers.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerCategory
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.IndexerCategories
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.ProwlarrIndexersRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One row of the categories manager: a default category and its usage. */
data class CategoryRow(
    val category: IndexerCategory,
    val depth: Int,
    val supportingIndexers: Int,
)

/**
 * State of the categories manager screen: the server's default category
 * tree with how many configured indexers cover each category.
 */
data class CategoriesUiState(
    val loaded: Boolean = false,
    val refreshing: Boolean = false,
    val loadError: ArrError? = null,
    val defaults: List<IndexerCategory> = emptyList(),
    val rows: List<CategoryRow> = emptyList(),
    val totalIndexers: Int = 0,
    val expanded: Set<Int> = emptySet(),
) {
    fun isExpanded(categoryId: Int): Boolean = categoryId in expanded
}

/**
 * ViewModel of the categories manager. The default categories endpoint is
 * read-only server data, so this screen presents the tree cross-referenced
 * with the cached indexer capabilities.
 */
class CategoriesViewModel(
    private val repository: ProwlarrIndexersRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CategoriesUiState())
    val state: StateFlow<CategoriesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeIndexers()
                .catch { }
                .collect { indexers -> rebuildRows(indexers) }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            // Indexers feed the usage counts, so pull them alongside the
            // tree; refreshIfStale is a no-op when the list screen already
            // filled the cache recently.
            repository.refreshIfStale()
            val categories = repository.defaultCategories(ttlMillis = 0)
            when (categories) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        loaded = true,
                        refreshing = false,
                        loadError = null,
                        defaults = categories.value,
                    )
                }
                is ArrResult.Err -> _state.update {
                    it.copy(loaded = true, refreshing = false, loadError = categories.error)
                }
            }
            rebuildRows(repository.cachedIndexers())
        }
    }

    fun toggleExpanded(categoryId: Int) {
        viewModelScope.launch {
            _state.update { s ->
                val next = if (categoryId in s.expanded) {
                    s.expanded - categoryId
                } else {
                    s.expanded + categoryId
                }
                s.copy(expanded = next)
            }
            rebuildRows(repository.cachedIndexers())
        }
    }

    private suspend fun rebuildRows(indexers: List<IndexerResource>) {
        val s = _state.value
        val coverage = HashMap<Int, Int>()
        indexers.forEach { indexer ->
            IndexerCategories.selectedIds(indexer).forEach { id ->
                coverage[id] = (coverage[id] ?: 0) + 1
            }
        }
        val rows = mutableListOf<CategoryRow>()
        fun walk(categories: List<IndexerCategory>, depth: Int) {
            val sorted = categories.sortedBy { it.id }
            sorted.forEach { category ->
                rows += CategoryRow(
                    category = category,
                    depth = depth,
                    supportingIndexers = coverage[category.id] ?: 0,
                )
                if (category.id in s.expanded) {
                    walk(category.subCategories.orEmpty(), depth + 1)
                }
            }
        }
        walk(s.defaults, 0)
        _state.update { it.copy(rows = rows, totalIndexers = indexers.size) }
    }
}
