package dev.appolon.homelab.arr.servers.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.ArrTime
import dev.appolon.homelab.arr.data.QueuedAction
import dev.appolon.homelab.arr.servers.R
import dev.appolon.homelab.arr.servers.vm.SyncStatusViewModel
import dev.appolon.homelab.arr.ui.components.SectionHeader
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/**
 * Offline action queue status: pending entries with their description,
 * attempt count, and age, a retry-now action per entry, retry-all in the app
 * bar, discard buttons, and the dead-letter list of actions the queue gave
 * up on after repeated failures.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncStatusScreen(
    state: SyncStatusViewModel.UiState,
    onRetryAll: () -> Unit,
    onRetryOne: (ActionId) -> Unit,
    onDiscard: (ActionId) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val lastResult = state.lastResult
    // Resolved in composition (not inside LaunchedEffect) because formatting
    // needs composable stringResource.
    val resultMessage = lastResult?.let { (sent, dead) ->
        stringResource(R.string.arr_servers_sync_result, sent, dead)
    }
    LaunchedEffect(resultMessage) {
        resultMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    ArrScaffold(
        title = stringResource(R.string.arr_servers_sync_title),
        onBack = onBack,
        modifier = modifier,
        actions = {
            IconButton(
                onClick = onRetryAll,
                enabled = !state.working && state.pending.isNotEmpty(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.arr_servers_sync_cd_retry_all),
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ArrLceBox(
                state = syncLce(state),
                onRetry = onRetry,
                emptyTitle = stringResource(R.string.arr_servers_sync_empty_title),
                emptyMessage = stringResource(R.string.arr_servers_sync_empty_message),
                modifier = Modifier.fillMaxSize(),
            ) { loaded ->
                SyncStatusContent(
                    state = loaded,
                    onRetryOne = onRetryOne,
                    onDiscard = onDiscard,
                )
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private fun syncLce(state: SyncStatusViewModel.UiState): LceState<SyncStatusViewModel.UiState> =
    when {
        state.loadError != null -> LceState.Error(state.loadError)
        !state.loaded -> LceState.Loading
        state.pending.isEmpty() && state.dead.isEmpty() -> LceState.Empty
        else -> LceState.Content(state, refreshing = state.working)
    }

@Composable
private fun SyncStatusContent(
    state: SyncStatusViewModel.UiState,
    onRetryOne: (ActionId) -> Unit,
    onDiscard: (ActionId) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (state.pending.isNotEmpty()) {
            item {
                SectionHeader(
                    title = stringResource(R.string.arr_servers_sync_pending_header),
                )
            }
            items(state.pending, key = { it.actionId.value }) { action ->
                ActionCard(
                    action = action,
                    onRetryOne = onRetryOne,
                    onDiscard = onDiscard,
                )
            }
        }

        if (state.dead.isNotEmpty()) {
            item {
                SectionHeader(
                    title = stringResource(R.string.arr_servers_sync_dead_header),
                )
            }
            item {
                Text(
                    text = stringResource(R.string.arr_servers_sync_dead_support),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(state.dead, key = { it.actionId.value }) { action ->
                ActionCard(
                    action = action,
                    onRetryOne = onRetryOne,
                    onDiscard = onDiscard,
                )
            }
        }
    }
}

@Composable
private fun ActionCard(
    action: QueuedAction,
    onRetryOne: (ActionId) -> Unit,
    onDiscard: (ActionId) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = action.description,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(
                    R.string.arr_servers_sync_action_path,
                    action.method.uppercase(),
                    action.path,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    R.string.arr_servers_sync_meta,
                    ArrTime.relative(action.enqueuedAtMillis),
                    action.attempts,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            action.lastError?.let { error ->
                Text(
                    text = stringResource(R.string.arr_servers_sync_last_error, error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(onClick = { onRetryOne(action.actionId) }) {
                    Text(stringResource(R.string.arr_servers_sync_retry))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onDiscard(action.actionId) }) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = stringResource(
                            R.string.arr_servers_sync_cd_discard,
                        ),
                    )
                }
            }
        }
    }
}
