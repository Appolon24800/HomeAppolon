package dev.appolon.homelab.arr.servers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.servers.R
import dev.appolon.homelab.arr.servers.notify.NotificationPreview
import dev.appolon.homelab.arr.servers.vm.NotificationPrefsViewModel
import dev.appolon.homelab.arr.ui.components.LabeledSwitch
import dev.appolon.homelab.arr.ui.components.SectionHeader
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/**
 * Per-server notification channel toggles (grab, import, fail, health, disk)
 * persisted through GlobalSettings, with a global kill switch and a preview
 * action that posts one real notification. The preview stays disabled while
 * the kill switch is off or the app lacks the notification permission.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationPrefsScreen(
    state: NotificationPrefsViewModel.UiState,
    onSetKillSwitch: (Boolean) -> Unit,
    onSetChannel: (ServerId, NotificationPrefsViewModel.Kind, Boolean) -> Unit,
    onPreview: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val canNotify = remember { NotificationPreview.canNotify(context) }

    ArrScaffold(
        title = stringResource(R.string.arr_servers_notify_title),
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ArrLceBox(
                state = notificationLce(state),
                emptyTitle = stringResource(R.string.arr_servers_empty_title),
                emptyMessage = stringResource(R.string.arr_servers_empty_message),
                modifier = Modifier.fillMaxSize(),
            ) { servers ->
                NotificationPrefsContent(
                    killSwitch = state.killSwitch,
                    canNotify = canNotify,
                    servers = servers,
                    onSetKillSwitch = onSetKillSwitch,
                    onSetChannel = onSetChannel,
                    onPreview = onPreview,
                )
            }
        }
    }
}

private fun notificationLce(
    state: NotificationPrefsViewModel.UiState,
): LceState<List<NotificationPrefsViewModel.ServerNotificationState>> = when {
    state.loadError != null -> LceState.Error(state.loadError)
    !state.loaded -> LceState.Loading
    state.servers.isEmpty() -> LceState.Empty
    else -> LceState.Content(state.servers)
}

@Composable
private fun NotificationPrefsContent(
    killSwitch: Boolean,
    canNotify: Boolean,
    servers: List<NotificationPrefsViewModel.ServerNotificationState>,
    onSetKillSwitch: (Boolean) -> Unit,
    onSetChannel: (ServerId, NotificationPrefsViewModel.Kind, Boolean) -> Unit,
    onPreview: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    LabeledSwitch(
                        label = stringResource(R.string.arr_servers_notify_kill),
                        checked = killSwitch,
                        onCheckedChange = onSetKillSwitch,
                        supportingText = stringResource(R.string.arr_servers_notify_kill_support),
                    )
                    if (!canNotify) {
                        Text(
                            text = stringResource(
                                R.string.arr_servers_notify_preview_unavailable,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        items(servers, key = { it.server.id.value }) { row ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = row.server.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = { onPreview(row.server.name) },
                            enabled = killSwitch && canNotify,
                        ) {
                            Text(stringResource(R.string.arr_servers_notify_preview))
                        }
                    }
                    ChannelSwitch(
                        label = stringResource(R.string.arr_servers_notify_channel_grab),
                        checked = row.channels.grab,
                        enabled = killSwitch,
                        onCheckedChange = {
                            onSetChannel(row.server.id, NotificationPrefsViewModel.Kind.GRAB, it)
                        },
                    )
                    ChannelSwitch(
                        label = stringResource(R.string.arr_servers_notify_channel_import),
                        checked = row.channels.`import`,
                        enabled = killSwitch,
                        onCheckedChange = {
                            onSetChannel(row.server.id, NotificationPrefsViewModel.Kind.IMPORT, it)
                        },
                    )
                    ChannelSwitch(
                        label = stringResource(R.string.arr_servers_notify_channel_fail),
                        checked = row.channels.fail,
                        enabled = killSwitch,
                        onCheckedChange = {
                            onSetChannel(row.server.id, NotificationPrefsViewModel.Kind.FAIL, it)
                        },
                    )
                    ChannelSwitch(
                        label = stringResource(R.string.arr_servers_notify_channel_health),
                        checked = row.channels.health,
                        enabled = killSwitch,
                        onCheckedChange = {
                            onSetChannel(row.server.id, NotificationPrefsViewModel.Kind.HEALTH, it)
                        },
                    )
                    ChannelSwitch(
                        label = stringResource(R.string.arr_servers_notify_channel_disk),
                        checked = row.channels.disk,
                        enabled = killSwitch,
                        onCheckedChange = {
                            onSetChannel(row.server.id, NotificationPrefsViewModel.Kind.DISK, it)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelSwitch(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    // A disabled LabeledSwitch would swallow taps on the whole row, so the
    // label is rendered as plain text and only the switch is disabled.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
