package dev.appolon.homelab.arr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.ui.R

/**
 * Sticky header for one day in a calendar-style list. Today's date gets a
 * primary badge and a trailing "Today" marker so it stands out while
 * scrolling.
 */
@Composable
fun CalendarDayHeader(
    dayLabel: String,
    dateLabel: String,
    modifier: Modifier = Modifier,
    isToday: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
    ) {
        if (isToday) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        } else {
            Text(text = dateLabel, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = dayLabel.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = if (isToday) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        if (isToday) {
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.arr_calendar_today),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
