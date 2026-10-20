package dev.appolon.homelab.arr.feature.bazarr.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.feature.bazarr.R
import dev.appolon.homelab.arr.feature.bazarr.settings.model.BazarrStatusRow
import dev.appolon.homelab.arr.feature.bazarr.settings.model.generalStatusRows
import dev.appolon.homelab.arr.feature.bazarr.settings.model.otherStatusRows
import dev.appolon.homelab.arr.feature.bazarr.settings.vm.GeneralUiState
import dev.appolon.homelab.arr.ui.components.SectionHeader
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/**
 * General settings: the settings-like keys of the status payload (folder
 * mappings, provider limits, captcha options when the server reports them)
 * above every remaining reported field. The bundled spec has no dedicated
 * settings endpoints, so the status projection is the source.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BazarrGeneralScreen(
    state: GeneralUiState,
    lce: LceState<GeneralUiState>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ArrScaffold(
        title = stringResource(R.string.arr_bazarr_settings_home_general_title),
        onBack = onBack,
        actions = {
            val cd = stringResource(R.string.arr_bazarr_cd_refresh)
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.semantics { contentDescription = cd },
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = cd)
            }
        },
        modifier = modifier,
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            val contentWidth = if (maxWidth >= 840.dp) 720.dp else maxWidth
            ArrLceBox(
                state = lce,
                onRetry = onRefresh,
                emptyTitle = stringResource(R.string.arr_bazarr_settings_general_empty_title),
                emptyMessage = stringResource(R.string.arr_bazarr_settings_general_empty_message),
                modifier = Modifier.fillMaxSize(),
            ) { _ ->
                ArrPullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val general = state.rows.generalStatusRows()
                    val other = state.rows.otherStatusRows()
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (general.isNotEmpty()) {
                            item(key = "general_header") {
                                SectionHeader(
                                    title = stringResource(
                                        R.string.arr_bazarr_settings_general_reported,
                                    ),
                                    modifier = Modifier.fillMaxWidth().widthIn(max = contentWidth),
                                )
                            }
                            items(general, key = { "g_${it.key}" }) { row ->
                                StatusRow(
                                    row = row,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = contentWidth),
                                )
                            }
                        }
                        if (other.isNotEmpty()) {
                            item(key = "other_header") {
                                SectionHeader(
                                    title = stringResource(
                                        R.string.arr_bazarr_settings_general_all_fields,
                                    ),
                                    modifier = Modifier.fillMaxWidth().widthIn(max = contentWidth),
                                )
                            }
                            items(other, key = { "o_${it.key}" }) { row ->
                                StatusRow(
                                    row = row,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = contentWidth),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(
    row: BazarrStatusRow,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(text = row.key, style = MaterialTheme.typography.bodyMedium) },
        supportingContent = {
            Text(
                text = row.value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = modifier,
    )
}
