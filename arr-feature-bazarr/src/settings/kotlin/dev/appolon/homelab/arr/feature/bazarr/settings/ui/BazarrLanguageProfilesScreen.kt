package dev.appolon.homelab.arr.feature.bazarr.settings.ui

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.feature.bazarr.R
import dev.appolon.homelab.arr.feature.bazarr.episodes.ui.languageLabel
import dev.appolon.homelab.arr.feature.bazarr.settings.model.BazarrLanguageProfile
import dev.appolon.homelab.arr.feature.bazarr.settings.vm.ProfilesUiState
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/**
 * Language profiles: one card per profile laying out the rule set the server
 * enforces, namely the cutoff language, the ordered language slots with
 * hearing impaired and forced flags, and the must-contain, must-not-contain,
 * and original-format release rules.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BazarrLanguageProfilesScreen(
    state: ProfilesUiState,
    lce: LceState<ProfilesUiState>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ArrScaffold(
        title = stringResource(R.string.arr_bazarr_settings_home_profiles_title),
        onBack = onBack,
        actions = {
            val cd = stringResource(R.string.arr_bazarr_cd_refresh)
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.semantics { contentDescription = cd },
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = cd)
            }
        },
        modifier = modifier,
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            val contentWidth = if (maxWidth >= 840.dp) 720.dp else maxWidth
            ArrLceBox(
                state = lce,
                onRetry = onRefresh,
                emptyTitle = stringResource(R.string.arr_bazarr_settings_profiles_empty_title),
                emptyMessage = stringResource(R.string.arr_bazarr_settings_profiles_empty_message),
                modifier = Modifier.fillMaxSize(),
            ) { _ ->
                ArrPullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.profiles, key = { it.profileId }) { profile ->
                            ProfileCard(
                                profile = profile,
                                modifier = Modifier.fillMaxWidth().widthIn(max = contentWidth),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: BazarrLanguageProfile,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = profile.label,
                style = MaterialTheme.typography.titleSmall,
            )
            if (profile.cutoff.isNotBlank()) {
                Text(
                    text = stringResource(
                        R.string.arr_bazarr_settings_profiles_cutoff,
                        profile.cutoff,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (profile.items.isNotEmpty()) {
                val hiLabel = stringResource(R.string.arr_bazarr_hearing_impaired)
                val forcedLabel = stringResource(R.string.arr_bazarr_forced)
                val slotsTitle =
                    stringResource(R.string.arr_bazarr_settings_profiles_slots)
                ProfileSection(title = slotsTitle) {
                    profile.items.forEach { slot ->
                        Text(
                            text = buildString {
                                append(languageLabel(slot.code2, ""))
                                if (slot.hearingImpaired) append(" · ").append(hiLabel)
                                if (slot.forced) append(" · ").append(forcedLabel)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            RuleSection(
                title = stringResource(R.string.arr_bazarr_settings_profiles_must_contain),
                rules = profile.mustContain,
            )
            RuleSection(
                title = stringResource(R.string.arr_bazarr_settings_profiles_must_not_contain),
                rules = profile.mustNotContain,
            )
            RuleSection(
                title = stringResource(R.string.arr_bazarr_settings_profiles_original_format),
                rules = profile.originalFormat,
            )
        }
    }
}

@Composable
private fun ProfileSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

/** Rule group that collapses to a "no rules" line when the list is empty. */
@Composable
private fun RuleSection(
    title: String,
    rules: List<String>,
) {
    ProfileSection(title = title) {
        if (rules.isEmpty()) {
            Text(
                text = stringResource(R.string.arr_bazarr_settings_profiles_no_rules),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            rules.forEach { rule ->
                Text(
                    text = rule,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
