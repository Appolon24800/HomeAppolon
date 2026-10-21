package dev.appolon.homelab.arr.feature.lidarr.search.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.api.lidarr.model.Quality
import dev.appolon.homelab.arr.api.lidarr.model.ReleaseResource
import dev.appolon.homelab.arr.feature.lidarr.R

/**
 * The reusable grab sheet: shows one release and lets the caller's user
 * override its quality before the grab. Lidarr's release wire model carries
 * no languages, so quality is the only override; a language pick would be
 * dropped by the server. Designed to be hosted by any Lidarr screen group
 * (detail, wanted, calendar, search); it owns no state beyond the dropdown
 * and reports every choice upward.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LidarrGrabSheet(
    release: ReleaseResource,
    qualityOptions: List<Quality>,
    busy: Boolean,
    onGrab: (quality: Quality?) -> Unit,
    onDismiss: () -> Unit,
) {
    val parsedQuality = release.quality?.quality
    val qualities = remember(release, qualityOptions) {
        (listOfNotNull(parsedQuality) + qualityOptions).distinctBy { it.id }
    }
    var quality by remember(release.guid, release.title) { mutableStateOf(parsedQuality) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.arr_lidarr_grab_sheet_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = release.title.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            OptionDropdown(
                label = stringResource(R.string.arr_lidarr_grab_sheet_quality_label),
                chosenName = quality?.name,
                fallback = stringResource(R.string.arr_lidarr_grab_sheet_as_parsed),
                contentDescription = stringResource(R.string.arr_lidarr_grab_sheet_cd_quality),
                optionNames = qualities.mapNotNull { it.name },
                onSelect = { name ->
                    quality = qualities.firstOrNull { it.name == name } ?: quality
                },
            )
            Spacer(Modifier.height(20.dp))
            if (busy) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
            }
            Row {
                Button(
                    onClick = { onGrab(quality) },
                    enabled = !busy,
                    modifier = Modifier.height(48.dp),
                ) {
                    Text(stringResource(R.string.arr_lidarr_grab_sheet_action))
                }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !busy,
                    modifier = Modifier.height(48.dp),
                ) {
                    Text(stringResource(R.string.arr_lidarr_grab_sheet_cancel))
                }
            }
        }
    }
}

/** Labeled dropdown with a 48dp target; [fallback] shows when nothing is chosen. */
@Composable
private fun OptionDropdown(
    label: String,
    chosenName: String?,
    fallback: String,
    contentDescription: String,
    optionNames: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .semantics { this.contentDescription = contentDescription },
        ) {
            Text(
                text = chosenName ?: fallback,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            optionNames.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        expanded = false
                        onSelect(name)
                    },
                )
            }
        }
    }
}
