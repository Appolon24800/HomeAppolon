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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.feature.bazarr.R
import dev.appolon.homelab.arr.feature.bazarr.settings.vm.NotificationsMessage
import dev.appolon.homelab.arr.feature.bazarr.settings.vm.NotificationsUiState
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/**
 * Notifications: the inbound webhook matrix Bazarr exposes (Plex play
 * events, Radarr and Sonarr imports) with a one-tap probe of the external
 * webhook connection.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BazarrNotificationsScreen(
    state: NotificationsUiState,
    lce: LceState<NotificationsUiState>,
    onBack: () -> Unit,
    onTestWebhook: () -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val messageText = state.message?.let { notificationsMessageText(it) }
    LaunchedEffect(messageText) {
        messageText?.let {
            snackbar.showSnackbar(it)
            onConsumeMessage()
        }
    }

    ArrScaffold(
        title = stringResource(R.string.arr_bazarr_settings_home_notifications_title),
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            val contentWidth = if (maxWidth >= 840.dp) 720.dp else maxWidth
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize(),
            ) {
                Text(
                    text = stringResource(R.string.arr_bazarr_settings_notifications_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = contentWidth)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
                val rows = webhookRows()
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(rows, key = { it.title }) { row ->
                        ListItem(
                            leadingContent = {
                                Icon(
                                    imageVector = row.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            headlineContent = {
                                Text(text = row.title, style = MaterialTheme.typography.titleSmall)
                            },
                            supportingContent = { Text(text = row.message) },
                            modifier = Modifier.fillMaxWidth().widthIn(max = contentWidth),
                        )
                    }
                    item(key = "test") {
                        val testCd =
                            stringResource(R.string.arr_bazarr_settings_notifications_cd_test)
                        Button(
                            onClick = onTestWebhook,
                            enabled = !state.testing,
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .semantics { contentDescription = testCd },
                        ) {
                            if (state.testing) {
                                val testingCd =
                                    stringResource(R.string.arr_bazarr_cd_testing)
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .semantics { contentDescription = testingCd },
                                )
                            } else {
                                Icon(Icons.Filled.Bolt, contentDescription = null)
                            }
                            Text(
                                text = stringResource(
                                    R.string.arr_bazarr_settings_notifications_test,
                                ),
                                modifier = Modifier.padding(start = 8.dp),
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

private data class WebhookRow(
    val icon: ImageVector,
    val title: String,
    val message: String,
)

@Composable
private fun webhookRows(): List<WebhookRow> = listOf(
    WebhookRow(
        icon = Icons.Filled.PlayCircle,
        title = stringResource(R.string.arr_bazarr_settings_notifications_plex_title),
        message = stringResource(R.string.arr_bazarr_settings_notifications_plex_message),
    ),
    WebhookRow(
        icon = Icons.Filled.Movie,
        title = stringResource(R.string.arr_bazarr_settings_notifications_radarr_title),
        message = stringResource(R.string.arr_bazarr_settings_notifications_radarr_message),
    ),
    WebhookRow(
        icon = Icons.Filled.Tv,
        title = stringResource(R.string.arr_bazarr_settings_notifications_sonarr_title),
        message = stringResource(R.string.arr_bazarr_settings_notifications_sonarr_message),
    ),
)

/** Snackbar text for the webhook probe outcomes. */
@Composable
internal fun notificationsMessageText(message: NotificationsMessage): String = when (message) {
    NotificationsMessage.TestSucceeded ->
        stringResource(R.string.arr_bazarr_settings_notifications_test_synced)

    is NotificationsMessage.TestFailed -> arrErrorMessage(message.error)
}
