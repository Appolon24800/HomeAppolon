package dev.appolon.homelab.arr.servers.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.VpnLock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.servers.R
import dev.appolon.homelab.arr.servers.vm.ServerListViewModel
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/**
 * The list of configured servers: one card per server with its app type,
 * base URL, and a reachability dot, plus add, reorder, and delete controls.
 * Everything below the app bar renders through [ArrLceBox] so registry
 * failures become an error pane, never a crash.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(
    lce: LceState<List<ArrServer>>,
    health: Map<ServerId, ServerListViewModel.Health>,
    probing: Boolean,
    onAdd: () -> Unit,
    onEdit: (ServerId) -> Unit,
    onMoveUp: (ServerId) -> Unit,
    onMoveDown: (ServerId) -> Unit,
    onDelete: (ServerId) -> Unit,
    onOpenProxy: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSync: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<ArrServer?>(null) }

    ArrScaffold(
        title = stringResource(R.string.arr_servers_title),
        subtitle = if (probing) stringResource(R.string.arr_servers_probing) else null,
        modifier = modifier,
        actions = {
            IconButton(onClick = onOpenProxy) {
                Icon(
                    imageVector = Icons.Outlined.VpnLock,
                    contentDescription = stringResource(R.string.arr_servers_cd_proxy),
                )
            }
            IconButton(onClick = onOpenNotifications) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = stringResource(R.string.arr_servers_cd_notifications),
                )
            }
            IconButton(onClick = onOpenSync) {
                Icon(
                    imageVector = Icons.Outlined.Sync,
                    contentDescription = stringResource(R.string.arr_servers_cd_sync),
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
                state = lce,
                onRetry = onRetry,
                emptyTitle = stringResource(R.string.arr_servers_empty_title),
                emptyMessage = stringResource(R.string.arr_servers_empty_message),
                modifier = Modifier.fillMaxSize(),
            ) { servers ->
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(servers, key = { _, server -> server.id.value }) { index, server ->
                        ServerCard(
                            server = server,
                            health = health[server.id] ?: ServerListViewModel.Health.UNKNOWN,
                            isFirst = index == 0,
                            isLast = index == servers.lastIndex,
                            onEdit = { },
                            onMoveUp = { onMoveUp(server.id) },
                            onMoveDown = { onMoveDown(server.id) },
                            onRequestDelete = { pendingDelete = server },
                        )
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }

    pendingDelete?.let { server ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.arr_servers_delete_title)) },
            text = { Text(stringResource(R.string.arr_servers_delete_message, server.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDelete(server.id)
                    },
                ) {
                    Text(stringResource(R.string.arr_servers_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.arr_servers_delete_cancel))
                }
            },
        )
    }
}

@Composable
private fun ServerCard(
    server: ArrServer,
    health: ServerListViewModel.Health,
    isFirst: Boolean,
    isLast: Boolean,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    Card(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        ) {
            HealthDot(health = health, serverName = server.name)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = server.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.arr_servers_card_subtitle,
                        server.type.displayName,
                        server.baseUrl,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onMoveUp, enabled = !isFirst) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(
                        R.string.arr_servers_cd_move_up,
                        server.name,
                    ),
                )
            }
            IconButton(onClick = onMoveDown, enabled = !isLast) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(
                        R.string.arr_servers_cd_move_down,
                        server.name,
                    ),
                )
            }
            IconButton(onClick = onRequestDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = stringResource(
                        R.string.arr_servers_cd_delete,
                        server.name,
                    ),
                )
            }
        }
    }
}

/** 10 dp status dot; green reachable, red unreachable, gray unknown. */
@Composable
private fun HealthDot(health: ServerListViewModel.Health, serverName: String) {
    val description = when (health) {
        ServerListViewModel.Health.OK ->
            stringResource(R.string.arr_servers_cd_health_ok, serverName)
        ServerListViewModel.Health.UNREACHABLE ->
            stringResource(R.string.arr_servers_cd_health_unreachable, serverName)

        ServerListViewModel.Health.UNKNOWN ->
            stringResource(R.string.arr_servers_cd_health_unknown, serverName)
    }
    val color = when (health) {
        ServerListViewModel.Health.OK -> HealthGreen
        ServerListViewModel.Health.UNREACHABLE -> HealthRed
        ServerListViewModel.Health.UNKNOWN ->
            MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .semantics { contentDescription = description }
            .background(color, CircleShape),
    )
}

// Plain status colors that read on both themes; the dot never carries the
// only signal (the card subtitle and edit flow also show reachability).
private val HealthGreen = Color(0xFF4CAF50)
private val HealthRed = Color(0xFFEF5350)
