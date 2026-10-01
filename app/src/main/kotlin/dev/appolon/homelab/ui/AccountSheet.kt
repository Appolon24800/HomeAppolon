package dev.appolon.homelab.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.data.HomeUiState
import dev.appolon.homelab.data.PocketId
import dev.appolon.homelab.data.PocketIdAccount
import dev.appolon.homelab.ui.components.AccountAvatar
import dev.appolon.homelab.ui.components.ErrorText

/**
 * PocketID account switcher: lists saved identities, switches the live
 * session, and offers add/sign-out. Displayed as a bottom sheet from the
 * dashboard's avatar chip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(state: HomeUiState, vm: HomeViewModel) {
    ModalBottomSheet(
        onDismissRequest = vm::closeAccountSheet,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // Explicit elevated surface: with Homer's dark palette the default
        // container is nearly black and the sheet melts into the scrim.
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
            ) {
                Text("Accounts", style = MaterialTheme.typography.titleMedium)
            }

            if (state.accountBusy) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp))
            }
            ErrorText(
                state.accountError,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            )

            if (state.accounts.isEmpty()) {
                Text(
                    "No accounts yet — add one to sign in to every service automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }

            state.accounts.forEach { account ->
                AccountRow(
                    account = account,
                    active = account.id == state.activeAccountId,
                    sessionLive = account.id == state.activeAccountId && state.accountUser != null,
                    enabled = !state.accountBusy,
                    avatarUrl = PocketId.accountAvatarUrl(state.accountUrl, account.id),
                    onSwitch = { vm.switchAccount(account.id) },
                    onRemove = { vm.removeAccount(account.id) },
                )
            }

            HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))

            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("Add account") },
                leadingContent = {
                    // 36dp like the avatars above, so all text columns align.
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                    }
                },
                modifier = Modifier.clickable(enabled = !state.accountBusy) { vm.openLogin() },
            )
            if (state.activeAccountId != null) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("Sign out") },
                    supportingContent = { Text("Accounts stay saved for quick switching") },
                    leadingContent = {
                        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                        }
                    },
                    modifier = Modifier.clickable(enabled = !state.accountBusy) { vm.signOut() },
                )
            }
        }
    }
}

@Composable
private fun AccountRow(
    account: PocketIdAccount,
    active: Boolean,
    sessionLive: Boolean,
    enabled: Boolean,
    avatarUrl: String,
    onSwitch: () -> Unit,
    onRemove: () -> Unit,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(account.label) },
        supportingContent = {
            Text(
                when {
                    active && sessionLive -> account.email ?: account.username
                    active -> account.email ?: account.username + " · session expired"
                    else -> account.email ?: account.username
                }
            )
        },
        leadingContent = { AccountAvatar(account.label, avatarUrl) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (active) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Active",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (!active) {
                    IconButton(onClick = onRemove, enabled = enabled) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove ${account.label}",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        },
        modifier = Modifier.clickable(enabled = enabled, onClick = onSwitch),
    )
}
