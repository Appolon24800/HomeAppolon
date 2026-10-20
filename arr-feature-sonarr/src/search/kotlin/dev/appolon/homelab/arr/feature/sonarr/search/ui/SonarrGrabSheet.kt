package dev.appolon.homelab.arr.feature.sonarr.search.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.api.sonarr.model.Language
import dev.appolon.homelab.arr.api.sonarr.model.Quality
import dev.appolon.homelab.arr.api.sonarr.model.ReleaseResource
import dev.appolon.homelab.arr.feature.sonarr.R
import dev.appolon.homelab.arr.ui.R as ArrUiR

/**
 * The reusable grab sheet: shows one release and lets the caller's user
 * override its quality and language before the grab. Designed to be hosted
 * by any Sonarr screen group (detail, wanted, calendar, search); it owns no
 * state beyond the two dropdowns and reports every choice upward.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonarrGrabSheet(
    release: ReleaseResource,
    qualityOptions: List<Quality>,
    languageOptions: List<Language>,
    busy: Boolean,
    onGrab: (quality: Quality?, language: Language?) -> Unit,
    onDismiss: () -> Unit,
) {
    val parsedQuality = release.quality.quality
    val parsedLanguage = release.languages.orEmpty().firstOrNull()
    val qualities = remember(release, qualityOptions) {
        (listOf(parsedQuality) + qualityOptions).distinctBy { it.id }
    }
    val languages = remember(release, languageOptions) {
        (listOfNotNull(parsedLanguage) + languageOptions).distinctBy { it.id }
    }
    var quality by remember(release.guid, release.title) { mutableStateOf(parsedQuality) }
    var language by remember(release.guid, release.title) { mutableStateOf(parsedLanguage) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(ArrUiR.string.arr_common_grab_sheet_title),
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
                label = stringResource(ArrUiR.string.arr_common_quality),
                chosenName = quality.name,
                fallback = stringResource(ArrUiR.string.arr_common_grab_sheet_as_parsed),
                contentDescription = stringResource(ArrUiR.string.arr_common_grab_sheet_cd_quality),
                optionNames = qualities.mapNotNull { it.name },
                onSelect = { name ->
                    quality = qualities.firstOrNull { it.name == name } ?: quality
                },
            )
            Spacer(Modifier.height(12.dp))
            OptionDropdown(
                label = stringResource(ArrUiR.string.arr_common_language),
                chosenName = language?.name,
                fallback = stringResource(ArrUiR.string.arr_common_grab_sheet_as_parsed),
                contentDescription = stringResource(ArrUiR.string.arr_common_grab_sheet_cd_language),
                optionNames = languages.mapNotNull { it.name },
                onSelect = { name ->
                    language = languages.firstOrNull { it.name == name } ?: language
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
                    onClick = { onGrab(quality, language) },
                    enabled = !busy,
                    modifier = Modifier.height(48.dp),
                ) {
                    Text(stringResource(ArrUiR.string.arr_common_grab_sheet_action))
                }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !busy,
                    modifier = Modifier.height(48.dp),
                ) {
                    Text(stringResource(ArrUiR.string.arr_common_cancel))
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
