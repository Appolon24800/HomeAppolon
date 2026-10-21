package dev.appolon.homelab.arr.feature.bazarr.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.feature.bazarr.R
import dev.appolon.homelab.arr.feature.bazarr.episodes.ui.QueuedBadge
import dev.appolon.homelab.arr.feature.bazarr.settings.model.BazarrProviderStatus
import dev.appolon.homelab.arr.feature.bazarr.settings.vm.ProvidersMessage
import dev.appolon.homelab.arr.feature.bazarr.settings.vm.ProvidersUiState
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/**
 * Subtitle providers: health rows with status, retry state, and any dynamic
 * fields the server reports, plus a confirm-guarded whole-fleet reset.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BazarrProvidersScreen(
    state: ProvidersUiState,
    lce: LceState<ProvidersUiState>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onResetProviders: () -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val messageText = state.message?.let { providersMessageText(it) }
    LaunchedEffect(messageText) {
        messageText?.let {
            snackbar.showSnackbar(it)
            onConsumeMessage()
        }
    }
    var confirmReset by remember { mutableStateOf(false) }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = {
                Text(stringResource(R.string.arr_bazarr_settings_providers_reset_confirm_title))
            },
            text = {
                Text(stringResource(R.string.arr_bazarr_settings_providers_reset_confirm_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        onResetProviders()
                    },
                ) {
                    Text(stringResource(R.string.arr_bazarr_settings_providers_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(stringResource(R.string.arr_bazarr_action_cancel))
                }
            },
        )
    }

    ArrScaffold(
        title = stringResource(R.string.arr_bazarr_settings_home_providers_title),
        onBack = onBack,
        actions = {
            if (state.resetting) {
                val resettingCd = stringResource(R.string.arr_bazarr_cd_loading)
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(12.dp)
                        .size(24.dp)
                        .semantics { contentDescription = resettingCd },
                )
            } else {
                val cd = stringResource(R.string.arr_bazarr_settings_providers_cd_reset)
                IconButton(
                    onClick = { confirmReset = true },
                    modifier = Modifier.semantics { contentDescription = cd },
                ) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = cd)
                }
            }
            val refreshCd = stringResource(R.string.arr_bazarr_cd_refresh)
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.semantics { contentDescription = refreshCd },
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = refreshCd)
            }
        },
        modifier = modifier,
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            val contentWidth = if (maxWidth >= 840.dp) 720.dp else maxWidth
            ArrLceBox(
                state = lce,
                onRetry = onRefresh,
                emptyTitle = stringResource(R.string.arr_bazarr_settings_providers_empty_title),
                emptyMessage = stringResource(R.string.arr_bazarr_settings_providers_empty_message),
                modifier = Modifier.fillMaxSize(),
            ) { _ ->
                ArrPullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (state.queuedReset) {
                            item(key = "queued_reset") {
                                QueuedBadge(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = contentWidth)
                                        .padding(bottom = 4.dp)
                                        // Polite live region: the queued-reset
                                        // notice appears without a focus move.
                                        .semantics { liveRegion = LiveRegionMode.Polite },
                                )
                            }
                        }
                        items(state.providers, key = { it.provider }) { provider ->
                            ProviderRow(
                                provider = provider,
                                modifier = Modifier.fillMaxWidth().widthIn(max = contentWidth),
                            )
                        }
                    }
                }
            }
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun ProviderRow(
    provider: BazarrProviderStatus,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        ListItem(
            headlineContent = {
                Text(text = provider.provider, style = MaterialTheme.typography.titleSmall)
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (provider.status.isNotBlank()) {
                        Text(
                            text = provider.status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (provider.retry.isNotBlank()) {
                        Text(
                            text = provider.retry,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    provider.extraFields.forEach { (name, value) ->
                        Text(
                            text = "$name: $value",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )
    }
}

/** Snackbar text for every provider write outcome. */
@Composable
internal fun providersMessageText(message: ProvidersMessage): String = when (message) {
    ProvidersMessage.ResetSynced ->
        stringResource(R.string.arr_bazarr_settings_providers_reset_synced)

    ProvidersMessage.ResetQueued ->
        stringResource(R.string.arr_bazarr_settings_providers_reset_queued)

    is ProvidersMessage.WriteFailed -> arrErrorMessage(message.error)
}
