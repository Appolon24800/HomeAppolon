package dev.appolon.homelab.arr.feature.prowlarr.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.feature.prowlarr.R
import dev.appolon.homelab.arr.ui.components.SectionHeader
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage
import dev.appolon.homelab.arr.ui.R as ArrUiR

/** Wide-screen list width cap; phones render full width. */
internal val ListMaxWidth = 640.dp

/** Chip marking rows with queued offline writes. */
@Composable
internal fun QueuedChip(modifier: Modifier = Modifier) {
    dev.appolon.homelab.arr.feature.prowlarr.indexers.ui.QueuedBadge(modifier)
}

/** Banner counting writes waiting for connectivity, shown above lists. */
@Composable
internal fun PendingBanner(pending: Int) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(stringResource(ArrUiR.string.arr_common_system_pending_banner, pending)) },
        // Polite live region: the pending count changes as the queue drains,
        // without focus moving here.
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Snackbar plumbing shared by every screen in this group. */
@Composable
internal fun NoticeHost(
    noticeMessage: String?,
    writeError: ArrError?,
    snackbarHostState: SnackbarHostState,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
) {
    val errorText = writeError?.let { arrErrorMessage(it) }
    LaunchedEffect(noticeMessage) {
        if (noticeMessage != null) {
            snackbarHostState.showSnackbar(noticeMessage)
            onConsumeNotice()
        }
    }
    LaunchedEffect(errorText) {
        if (errorText != null) {
            snackbarHostState.showSnackbar(errorText)
            onConsumeWriteError()
        }
    }
}

/** Read-only label/value row for status cards. */
@Composable
internal fun LabelRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp),
        )
        Text(
            value.ifBlank { stringResource(ArrUiR.string.arr_common_general_ssl_unset) },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Labeled section with a header, so screens share one spacing rhythm. */
@Composable
internal fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title = title, modifier = Modifier.semantics { heading() })
        content()
    }
}

/**
 * Dropdown over free-form string options: log levels, authentication
 * methods, UI cultures. Selection reports the raw wire value.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StringDropdownField(
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(
                    androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                )
                .fillMaxWidth(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
