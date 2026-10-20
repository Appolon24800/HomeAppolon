package dev.appolon.homelab.arr.feature.prowlarr.indexers.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.feature.prowlarr.R
import dev.appolon.homelab.arr.feature.prowlarr.indexers.vm.CategoriesUiState
import dev.appolon.homelab.arr.ui.components.SectionHeader
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState

/**
 * Categories manager: the server's default category tree with the number of
 * configured indexers covering each category. Read-only by design, the
 * endpoint is server defined.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    state: CategoriesUiState,
    lce: LceState<CategoriesUiState>,
    onRefresh: () -> Unit,
    onToggleExpanded: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    dev.appolon.homelab.arr.ui.shell.ArrScaffold(
        title = stringResource(R.string.arr_prowlarr_categories_title),
        subtitle = stringResource(R.string.arr_prowlarr_categories_subtitle, state.totalIndexers),
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        ArrPullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            ArrLceBox(
                state = lce,
                onRetry = onRefresh,
                emptyTitle = stringResource(R.string.arr_prowlarr_categories_empty),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) { content ->
                LazyColumn(Modifier.fillMaxSize()) {
                    items(content.rows, key = { it.category.id }) { row ->
                        CategoryManagerRow(
                            row = row,
                            onToggleExpanded = onToggleExpanded,
                            hasChildren = !row.category.subCategories.isNullOrEmpty(),
                            isExpanded = content.isExpanded(row.category.id),
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryManagerRow(
    row: dev.appolon.homelab.arr.feature.prowlarr.indexers.vm.CategoryRow,
    onToggleExpanded: (Int) -> Unit,
    hasChildren: Boolean,
    isExpanded: Boolean,
) {
    val expandCd = stringResource(
        if (isExpanded) R.string.arr_prowlarr_cd_collapse_category
        else R.string.arr_prowlarr_cd_expand_category,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = hasChildren) { onToggleExpanded(row.category.id) }
            .padding(start = (16 + row.depth * 16).dp, end = 16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${row.category.id} · ${row.category.name.orEmpty()}",
                style = MaterialTheme.typography.bodyLarge,
            )
            row.category.description?.let { description ->
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AssistChip(
            onClick = {},
            enabled = false,
            label = {
                Text(
                    stringResource(
                        R.string.arr_prowlarr_categories_supported,
                        row.supportingIndexers,
                    ),
                )
            },
        )
        if (hasChildren) {
            IconButton(
                onClick = { onToggleExpanded(row.category.id) },
                modifier = Modifier.semantics { contentDescription = expandCd },
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                )
            }
        }
    }
}
