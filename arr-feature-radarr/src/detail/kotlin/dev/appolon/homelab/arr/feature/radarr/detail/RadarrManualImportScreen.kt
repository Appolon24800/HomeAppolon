package dev.appolon.homelab.arr.feature.radarr.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.core.Humanize
import dev.appolon.homelab.arr.feature.radarr.R
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import dev.appolon.homelab.arr.ui.R as ArrUiR

/**
 * Interactive import for one movie: candidate files Radarr parsed inside the
 * movie's folder, per-file selection with rejection reasons, and the folder
 * scan command that performs the import.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarrManualImportScreen(
    state: RadarrManualImportUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onToggle: (Int) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onRevalidate: () -> Unit,
    onImport: () -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val message = importMessageText(state.message)
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            onConsumeMessage()
        }
    }
    val items = (state.items as? LceState.Content)?.data.orEmpty()
    val selectedCount = items.count { it.selected }

    ArrScaffold(
        title = stringResource(R.string.arr_radarr_detail_import_title),
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(Modifier.fillMaxSize()) {
                state.folder?.let {
                    Text(
                        text = stringResource(R.string.arr_radarr_detail_path_value, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    OutlinedButton(onClick = { onSelectAll(true) }, enabled = items.isNotEmpty()) {
                        Text(stringResource(ArrUiR.string.arr_common_import_flow_select_all))
                    }
                    OutlinedButton(onClick = { onSelectAll(false) }, enabled = items.isNotEmpty()) {
                        Text(stringResource(R.string.arr_radarr_detail_import_select_none))
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(
                        onClick = onRevalidate,
                        enabled = selectedCount > 0 && !state.importing,
                    ) {
                        Text(stringResource(R.string.arr_radarr_detail_import_revalidate))
                    }
                }
                ArrLceBox(
                    state = state.items,
                    onRetry = onRetry,
                    modifier = Modifier.weight(1f),
                    emptyTitle = stringResource(R.string.arr_radarr_detail_import_empty_title),
                    emptyMessage = stringResource(
                        if (state.folder == null) {
                            R.string.arr_radarr_detail_import_no_folder
                        } else {
                            R.string.arr_radarr_detail_import_empty_message
                        },
                    ),
                ) { list ->
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        items(list, key = { it.id }, contentType = { "import-item" }) { item ->
                            ImportItemCard(item = item, onToggle = onToggle)
                        }
                    }
                }
                Button(
                    onClick = onImport,
                    enabled = selectedCount > 0 && !state.importing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(
                        stringResource(R.string.arr_radarr_detail_import_action, selectedCount),
                    )
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun ImportItemCard(item: ManualImportItemUi, onToggle: (Int) -> Unit) {
    val toggleDescription = stringResource(
        ArrUiR.string.arr_common_detail_import_cd_select,
        item.relativePath,
    )
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(item.id) },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Checkbox(
                checked = item.selected,
                onCheckedChange = { onToggle(item.id) },
                modifier = Modifier.semantics { contentDescription = toggleDescription },
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    text = item.relativePath,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = listOfNotNull(
                    item.qualityName?.let(Humanize::qualityName),
                    item.languages.takeIf { it.isNotBlank() },
                    item.releaseGroup,
                    Humanize.bytes(item.sizeBytes),
                ).joinToString(" · ")
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.rejections.isNotBlank()) {
                    Text(
                        text = item.rejections,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun importMessageText(message: ImportMessage?): String? = when (message) {
    null -> null
    ImportMessage.ImportStarted ->
        stringResource(R.string.arr_radarr_detail_import_started)
    ImportMessage.ImportQueued ->
        stringResource(R.string.arr_radarr_detail_import_queued)
    is ImportMessage.ImportFailed -> arrErrorMessage(message.error)
}
