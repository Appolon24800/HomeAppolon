package dev.appolon.homelab.arr.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.ui.R

/**
 * Splits servers into chips shown in the switcher row and servers reachable only
 * through the overflow menu. Pure so the budget rule is unit-testable.
 */
fun splitServers(
    servers: List<ShellServer>,
    maxVisible: Int = 4,
): Pair<List<ShellServer>, List<ShellServer>> {
    require(maxVisible >= 0) { "maxVisible must not be negative" }
    return servers.take(maxVisible) to servers.drop(maxVisible)
}

/**
 * Bottom-docked server switcher: one filter chip per visible server (matching the
 * FilterChipRow look), an overflow chip for the rest, and the settings entry.
 * Chip taps keep Material 3's built-in 48dp minimum touch target enforcement.
 */
@Composable
fun ServerSwitcher(
    servers: List<ShellServer>,
    selectedId: ServerId?,
    onServerClick: (ShellServer) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    maxVisible: Int = 4,
) {
    val (visible, overflow) = splitServers(servers, maxVisible)
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val moreLabel = stringResource(R.string.arr_shell_more_servers, overflow.size)

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(visible, key = { it.id.value }) { server ->
            ServerChip(
                server = server,
                selected = server.id == selectedId,
                onClick = { onServerClick(server) },
            )
        }
        if (overflow.isNotEmpty()) {
            item(key = OverflowItemKey) {
                Box {
                    AssistChip(
                        onClick = { menuOpen = true },
                        label = { Text(moreLabel) },
                        modifier = Modifier.semantics { contentDescription = moreLabel },
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        overflow.forEach { server ->
                            DropdownMenuItem(
                                text = { Text(server.name) },
                                leadingIcon = { HealthIcon(server.healthy, server.name) },
                                onClick = {
                                    menuOpen = false
                                    onServerClick(server)
                                },
                            )
                        }
                    }
                }
            }
        }
        item(key = SettingsItemKey) {
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.arr_shell_cd_settings),
                )
            }
        }
    }
}

@Composable
private fun ServerChip(
    server: ShellServer,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val unhealthyCd = if (server.healthy) null else unhealthyDescription(server.name)
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(server.name, maxLines = 1) },
        leadingIcon = { HealthIcon(server.healthy, server.name) },
        modifier = Modifier.semantics {
            if (unhealthyCd != null) contentDescription = unhealthyCd
        },
    )
}

@Composable
private fun HealthIcon(healthy: Boolean, serverName: String) {
    if (healthy) {
        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
    } else {
        Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = unhealthyDescription(serverName),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun unhealthyDescription(serverName: String): String =
    stringResource(R.string.arr_shell_cd_server_unhealthy, serverName)

private const val OverflowItemKey = "__shell_overflow"
private const val SettingsItemKey = "__shell_settings"
