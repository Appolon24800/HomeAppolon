package dev.appolon.homelab.arr.feature.bazarr.history.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import dev.appolon.homelab.arr.feature.bazarr.history.model.BazarrBlacklistEntry
import dev.appolon.homelab.arr.feature.bazarr.history.vm.BazarrBlacklistUiState
import dev.appolon.homelab.arr.feature.bazarr.history.vm.BlacklistMessage
import dev.appolon.homelab.arr.feature.bazarr.history.vm.BlacklistTab
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Provider blacklist: blacklisted episode and movie subtitles, tabbed, each
 * row naming its provider, language, and media, with per-entry removal and a
 * confirm-guarded remove-all for the visible tab.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BazarrBlacklistScreen(
    state: BazarrBlacklistUiState,
    lce: LceState<BazarrBlacklistUiState>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onTab: (BlacklistTab) -> Unit,
    onRemove: (BazarrBlacklistEntry) -> Unit,
    onClearVisible: () -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val messageText = state.message?.let { blacklistMessageText(it) }
    LaunchedEffect(messageText) {
        messageText?.let {
            snackbar.showSnackbar(it)
            onConsumeMessage()
        }
    }
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.arr_bazarr_blacklist_clear_confirm_title)) },
            text = { Text(stringResource(R.string.arr_bazarr_blacklist_clear_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClearVisible()
                    },
                ) {
                    Text(stringResource(R.string.arr_bazarr_blacklist_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.arr_bazarr_action_cancel))
                }
            },
        )
    }

    ArrScaffold(
        title = stringResource(R.string.arr_bazarr_blacklist_title),
        onBack = onBack,
        actions = {
            val clearCd = stringResource(R.string.arr_bazarr_blacklist_clear)
            IconButton(
                onClick = { confirmClear = true },
                modifier = Modifier.semantics { contentDescription = clearCd },
            ) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = clearCd)
            }
            val cd = stringResource(R.string.arr_bazarr_cd_refresh)
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.semantics { contentDescription = cd },
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = cd)
            }
        },
        filterBar = {
            PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
                BlacklistTab.entries.forEach { tab ->
                    Tab(
                        selected = state.tab == tab,
                        onClick = { onTab(tab) },
                        text = {
                            Text(
                                when (tab) {
                                    BlacklistTab.EPISODES ->
                                        stringResource(R.string.arr_bazarr_blacklist_tab_episodes)

                                    BlacklistTab.MOVIES ->
                                        stringResource(R.string.arr_bazarr_blacklist_tab_movies)
                                },
                            )
                        },
                    )
                }
            }
        },
        modifier = modifier,
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            val contentWidth = if (maxWidth >= 840.dp) 800.dp else maxWidth
            ArrLceBox(
                state = lce,
                onRetry = onRefresh,
                emptyTitle = stringResource(R.string.arr_bazarr_blacklist_empty_title),
                emptyMessage = stringResource(R.string.arr_bazarr_blacklist_empty_message),
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
                        val queuedClear = if (state.tab == BlacklistTab.EPISODES) {
                            state.queuedClearEpisodes
                        } else {
                            state.queuedClearMovies
                        }
                        if (queuedClear) {
                            item(key = "queued_clear") {
                                QueuedBadge(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = contentWidth)
                                        .padding(bottom = 4.dp)
                                        // Polite live region: the queued-clear
                                        // notice appears without a focus move.
                                        .semantics { liveRegion = LiveRegionMode.Polite },
                                )
                            }
                        }
                        items(state.presented, key = { "${it.isMovie}_${it.key}" }) { entry ->
                            BlacklistRow(
                                entry = entry,
                                busy = entry.key in state.busyKeys,
                                queued = entry.key in state.queuedKeys,
                                onRemove = { onRemove(entry) },
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
private fun BlacklistRow(
    entry: BazarrBlacklistEntry,
    busy: Boolean,
    queued: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        ListItem(
            headlineContent = {
                Text(
                    text = entry.provider.ifBlank {
                        stringResource(R.string.arr_bazarr_history_unknown_action)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (entry.mediaLabel.isNotBlank()) {
                        Text(
                            text = entry.mediaLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val language = entry.languageName.ifBlank { entry.languageCode2 }
                        if (language.isNotBlank()) {
                            Text(
                                text = language,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (entry.timestampMillis > 0) {
                            Text(
                                text = formatTimestamp(entry.timestampMillis),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (entry.path.isNotBlank()) {
                        Text(
                            text = entry.path,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (queued) QueuedBadge()
                    if (busy) {
                        val busyCd = stringResource(R.string.arr_bazarr_cd_loading)
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .semantics { contentDescription = busyCd },
                            strokeWidth = 2.dp,
                        )
                    }
                    val cd = stringResource(R.string.arr_bazarr_blacklist_remove_cd)
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .semantics { contentDescription = cd },
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = cd)
                    }
                }
            },
        )
    }
}

/** Snackbar text for every blacklist write outcome. */
@Composable
internal fun blacklistMessageText(message: BlacklistMessage): String = when (message) {
    BlacklistMessage.RemovedSynced -> stringResource(R.string.arr_bazarr_blacklist_removed_synced)
    BlacklistMessage.RemovedQueued -> stringResource(R.string.arr_bazarr_blacklist_removed_queued)
    BlacklistMessage.ClearedSynced -> stringResource(R.string.arr_bazarr_blacklist_cleared_synced)
    BlacklistMessage.ClearedQueued -> stringResource(R.string.arr_bazarr_blacklist_cleared_queued)
    is BlacklistMessage.WriteFailed -> arrErrorMessage(message.error)
}

/** Local-time short timestamp; epoch 0 events render no date at all. */
private fun formatTimestamp(millis: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(millis))
