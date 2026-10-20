package dev.appolon.homelab.arr.feature.radarr.settings.quality.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.feature.radarr.R
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage

/** Section heading shared by every screen in this group. */
@Composable
internal fun QualitySectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

/** Outlined field for settings values, with optional inline error text. */
@Composable
internal fun QualityTextField(
    value: String,
    label: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    suffix: String? = null,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        supportingText = {
            val message = if (isError) errorMessage else supporting
            if (message != null) Text(message)
        },
        isError = isError,
        suffix = suffix?.let { { Text(it) } },
        minLines = minLines,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Exposed dropdown for a closed set of choices. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> QualityDropdown(
    label: String,
    selected: T,
    entries: List<T>,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = labelOf(selected),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(labelOf(entry)) },
                    onClick = {
                        onSelect(entry)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Weight row: label plus integer slider between the server limits, with the
 * current value announced for screen readers through the value label.
 */
@Composable
internal fun QualityWeightSlider(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onValue: (Int) -> Unit,
) {
    val clampedMax = max.coerceAtLeast(min)
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value.toFloat().coerceIn(min.toFloat(), clampedMax.toFloat()),
            onValueChange = { raw -> onValue(raw.toInt()) },
            valueRange = min.toFloat()..clampedMax.toFloat(),
            steps = (clampedMax - min).coerceAtMost(1000).let { range ->
                // A step per whole number when the span is small, otherwise a
                // coarse drag that still snaps to integers.
                if (range <= 100) range - 1 else 19
            },
        )
    }
}

/** Card wrapper giving the group's editor rows a consistent surface. */
@Composable
internal fun QualityCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            content()
        }
    }
}

/** Pending offline writes indicator shown while this feature has queued actions. */
@Composable
internal fun QualityPendingSyncBanner(pending: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.CloudUpload,
            contentDescription = stringResource(R.string.arr_radarr_quality_cd_queued_indicator),
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.arr_radarr_quality_pending_banner, pending),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

/** Hosts one-shot notices and write errors as snackbars, mm-style. */
@Composable
internal fun QualityNoticeHost(
    noticeMessage: String?,
    writeError: ArrError?,
    snackbarHostState: SnackbarHostState,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
) {
    LaunchedEffect(noticeMessage) {
        if (noticeMessage != null) {
            snackbarHostState.showSnackbar(noticeMessage)
            onConsumeNotice()
        }
    }
    val errorText = writeError?.let { arrErrorMessage(it) }
    LaunchedEffect(errorText) {
        if (errorText != null) {
            snackbarHostState.showSnackbar(errorText)
            onConsumeWriteError()
        }
    }
}

/** Whole-row navigation link with a 48dp minimum touch target. */
@Composable
internal fun QualityLinkRow(
    title: String,
    supporting: String? = null,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = title, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}
