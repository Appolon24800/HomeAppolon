package dev.appolon.homelab.arr.feature.bazarr.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.feature.bazarr.R
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/** One settings section entry: where it goes and what it covers. */
private data class SettingsSection(
    val title: String,
    val message: String,
    val onClick: () -> Unit,
)

/**
 * Settings home: the section list of every Bazarr settings screen. Rows are
 * plain navigation entries; each section owns its own screen and data.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BazarrSettingsHomeScreen(
    onBack: () -> Unit,
    onOpenLanguages: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenScheduler: () -> Unit,
    onOpenGeneral: () -> Unit,
    onOpenSystem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sections = listOf(
        SettingsSection(
            title = stringResource(R.string.arr_bazarr_settings_home_languages_title),
            message = stringResource(R.string.arr_bazarr_settings_home_languages_message),
            onClick = onOpenLanguages,
        ),
        SettingsSection(
            title = stringResource(R.string.arr_bazarr_settings_home_profiles_title),
            message = stringResource(R.string.arr_bazarr_settings_home_profiles_message),
            onClick = onOpenProfiles,
        ),
        SettingsSection(
            title = stringResource(R.string.arr_bazarr_settings_home_providers_title),
            message = stringResource(R.string.arr_bazarr_settings_home_providers_message),
            onClick = onOpenProviders,
        ),
        SettingsSection(
            title = stringResource(R.string.arr_bazarr_settings_home_notifications_title),
            message = stringResource(R.string.arr_bazarr_settings_home_notifications_message),
            onClick = onOpenNotifications,
        ),
        SettingsSection(
            title = stringResource(R.string.arr_bazarr_settings_home_scheduler_title),
            message = stringResource(R.string.arr_bazarr_settings_home_scheduler_message),
            onClick = onOpenScheduler,
        ),
        SettingsSection(
            title = stringResource(R.string.arr_bazarr_settings_home_general_title),
            message = stringResource(R.string.arr_bazarr_settings_home_general_message),
            onClick = onOpenGeneral,
        ),
        SettingsSection(
            title = stringResource(R.string.arr_bazarr_settings_home_system_title),
            message = stringResource(R.string.arr_bazarr_settings_home_system_message),
            onClick = onOpenSystem,
        ),
    )
    ArrScaffold(
        title = stringResource(R.string.arr_bazarr_settings_title),
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            val contentWidth = if (maxWidth >= 840.dp) 720.dp else maxWidth
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(sections) { section ->
                    SettingsSectionRow(
                        title = section.title,
                        message = section.message,
                        onClick = section.onClick,
                        modifier = Modifier.fillMaxWidth().widthIn(max = contentWidth),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionRow(
    title: String,
    message: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(text = title, style = MaterialTheme.typography.titleSmall) },
        supportingContent = {
            Text(text = message, style = MaterialTheme.typography.bodySmall)
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = modifier.clickable(onClick = onClick),
    )
}
