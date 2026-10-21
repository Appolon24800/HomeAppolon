package dev.appolon.homelab.arr.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.ui.R

/** Display status of a queue item, shared by every *arr queue screen. */
enum class QueueRowStatus { DOWNLOADING, PAUSED, QUEUED, IMPORTING, COMPLETED, FAILED }

/**
 * One download in the activity queue: status icon, title line with an
 * [actions] slot (pause, remove), subtitle, progress bar, and a preformatted
 * [speedText] built with [formatDataRate] and [formatEta]. A null [progress]
 * means the total size is unknown and the bar runs indeterminately.
 */
@Composable
fun QueueRow(
    title: String,
    status: QueueRowStatus,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    progress: Float? = null,
    speedText: String? = null,
    onClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Icon(
            imageVector = status.icon(),
            contentDescription = stringResource(R.string.arr_queue_status_cd, queueStatusLabel(status)),
            tint = when (status) {
                QueueRowStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
                QueueRowStatus.FAILED -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                actions()
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val clamped = progress?.coerceIn(0f, 1f)
            val barModifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 2.dp)
            if (clamped == null) {
                LinearProgressIndicator(modifier = barModifier)
            } else {
                LinearProgressIndicator(progress = { clamped }, modifier = barModifier)
            }
            if (speedText != null) {
                Text(
                    text = speedText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Localized label for a queue status, used in icon descriptions and detail rows. */
@Composable
fun queueStatusLabel(status: QueueRowStatus): String = stringResource(
    when (status) {
        QueueRowStatus.DOWNLOADING -> R.string.arr_status_downloading
        QueueRowStatus.PAUSED -> R.string.arr_status_paused
        QueueRowStatus.QUEUED -> R.string.arr_status_queued
        QueueRowStatus.IMPORTING -> R.string.arr_status_importing
        QueueRowStatus.COMPLETED -> R.string.arr_status_completed
        QueueRowStatus.FAILED -> R.string.arr_status_failed
    },
)

private fun QueueRowStatus.icon(): ImageVector = when (this) {
    QueueRowStatus.DOWNLOADING -> Icons.Filled.Download
    QueueRowStatus.PAUSED -> Icons.Filled.Pause
    QueueRowStatus.QUEUED -> Icons.Filled.Schedule
    QueueRowStatus.IMPORTING -> Icons.Filled.Input
    QueueRowStatus.COMPLETED -> Icons.Filled.CheckCircle
    QueueRowStatus.FAILED -> Icons.Filled.Error
}
