package dev.appolon.homelab.arr.feature.radarr.settings.mm.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.api.radarr.model.FileSystemEntityType
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.radarr.R
import dev.appolon.homelab.arr.feature.radarr.settings.mm.data.FileSystemListing
import dev.appolon.homelab.arr.feature.radarr.settings.mm.data.RadarrFileSystemRepository
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage
import dev.appolon.homelab.arr.ui.R as ArrUiR

/**
 * Server-side folder picker over the FileSystem endpoints. Tapping a
 * directory descends into it, the up button climbs to the server-reported
 * parent, and the confirm button returns the current path. The listing is
 * live (never cached) so what the dialog shows is what the server sees.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileSystemBrowserDialog(
    repository: RadarrFileSystemRepository,
    initialPath: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var path by remember { mutableStateOf(initialPath) }
    var listing by remember { mutableStateOf<ArrResult<FileSystemListing>?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(path, reload) {
        listing = null
        listing = repository.list(path)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(ArrUiR.string.arr_common_mm_browser_title)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val parent = (listing as? ArrResult.Ok)?.value?.parent
                    IconButton(
                        onClick = { path = parent },
                        enabled = parent != null,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowUpward,
                            contentDescription = stringResource(
                                ArrUiR.string.arr_common_mm_browser_cd_up,
                            ),
                        )
                    }
                    Text(
                        text = path ?: stringResource(ArrUiR.string.arr_common_mm_browser_root),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                    )
                }
                when (val result = listing) {
                    null -> Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }

                    is ArrResult.Err -> Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                    ) {
                        Text(
                            text = arrErrorMessage(result.error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = { reload += 1 }) {
                            Text(stringResource(ArrUiR.string.arr_common_retry))
                        }
                    }

                    is ArrResult.Ok -> {
                        val page = result.value
                        if (page.directories.isEmpty()) {
                            Text(
                                text = stringResource(ArrUiR.string.arr_common_mm_browser_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            ) {
                                items(
                                    page.directories,
                                    key = { it.path ?: it.name ?: "" },
                                    contentType = { "directory" },
                                ) { entry ->
                                    val name = entry.name
                                        ?: entry.path?.trimEnd('/')?.substringAfterLast('/')
                                        ?: ""
                                    val isDrive =
                                        entry.type == FileSystemEntityType.DRIVE
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .clickable(
                                                onClickLabel = stringResource(
                                                    ArrUiR.string.arr_common_mm_browser_cd_open,
                                                    name,
                                                ),
                                                onClick = { path = entry.path },
                                            )
                                            .padding(vertical = 4.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (isDrive) {
                                                Icons.Filled.Storage
                                            } else {
                                                Icons.Filled.Folder
                                            },
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPick(path ?: return@TextButton) },
                enabled = path != null,
            ) {
                Text(stringResource(ArrUiR.string.arr_common_mm_browser_use))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(ArrUiR.string.arr_common_cancel))
            }
        },
    )
}
