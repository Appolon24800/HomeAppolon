package dev.appolon.homelab.arr.feature.sonarr.settings.quality

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.ui.SonarrCustomFormatEditorDestination
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.ui.SonarrCustomFormatsDestination
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.ui.SonarrDelayProfilesDestination
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.ui.SonarrLanguageProfileEditorDestination
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.ui.SonarrLanguageProfilesDestination
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.ui.SonarrLanguagesDestination
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.ui.SonarrQualityDefinitionsDestination
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.ui.SonarrQualityProfileEditorDestination
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.ui.SonarrQualityProfilesDestination
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.ui.SonarrReleaseProfilesDestination
import dev.appolon.homelab.arr.ui.components.EmptyState
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.nav.matchRoutePattern
import dev.appolon.homelab.arr.ui.shell.ArrShellContent
import dev.appolon.homelab.arr.ui.shell.ArrShellNavigator
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator

/**
 * Everything the arr shell needs from the Sonarr quality and profiles
 * settings group: nested route patterns, open helpers, and the slot
 * composable for [ArrShellContent]. The integration layer calls
 * [register] once before composing the shell.
 */
object SonarrSettingsQualityFeature {
    /** Shared with the other Sonarr screen groups; patterns are settings-specific. */
    const val ROUTE_OWNER = "sonarr"

    /** Quality definitions editor: titles, weights, size bounds. */
    const val QUALITY_DEFINITIONS_PATTERN = "settings/quality/definitions/{serverId}"

    /** Quality profiles list. */
    const val QUALITY_PROFILES_PATTERN = "settings/quality/profiles/{serverId}"

    /** Schema-driven quality profile builder; profileId "new" creates. */
    const val QUALITY_PROFILE_EDITOR_PATTERN = "settings/quality/profile/{serverId}/{profileId}"

    /** Language profiles list; the table Radarr does not have. */
    const val LANGUAGE_PROFILES_PATTERN = "settings/quality/languageprofiles/{serverId}"

    /** Schema-driven language profile builder; profileId "new" creates. */
    const val LANGUAGE_PROFILE_EDITOR_PATTERN = "settings/quality/languageprofile/{serverId}/{profileId}"

    /** Custom formats list. */
    const val CUSTOM_FORMATS_PATTERN = "settings/quality/customformats/{serverId}"

    /** Custom format builder with condition editors; formatId "new" creates. */
    const val CUSTOM_FORMAT_EDITOR_PATTERN = "settings/quality/customformat/{serverId}/{formatId}"

    /** Delay profiles with reorder and protocol-specific delays. */
    const val DELAY_PROFILES_PATTERN = "settings/quality/delayprofiles/{serverId}"

    /** Release profiles with must / must-not-contain term lists. */
    const val RELEASE_PROFILES_PATTERN = "settings/quality/releaseprofiles/{serverId}"

    /** Read-only language lookup table. */
    const val LANGUAGES_PATTERN = "settings/quality/languages/{serverId}"

    val PATTERNS = listOf(
        QUALITY_DEFINITIONS_PATTERN,
        QUALITY_PROFILES_PATTERN,
        QUALITY_PROFILE_EDITOR_PATTERN,
        LANGUAGE_PROFILES_PATTERN,
        LANGUAGE_PROFILE_EDITOR_PATTERN,
        CUSTOM_FORMATS_PATTERN,
        CUSTOM_FORMAT_EDITOR_PATTERN,
        DELAY_PROFILES_PATTERN,
        RELEASE_PROFILES_PATTERN,
        LANGUAGES_PATTERN,
    )

    fun register(registry: ArrRouteRegistry) {
        PATTERNS.forEach { pattern -> registry.register(ROUTE_OWNER, pattern) }
    }

    fun qualityDefinitionsRoute(serverId: ServerId): ArrRoute.Feature? =
        fillPattern(QUALITY_DEFINITIONS_PATTERN, mapOf("serverId" to serverId.value))
            ?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun qualityProfilesRoute(serverId: ServerId): ArrRoute.Feature? =
        fillPattern(QUALITY_PROFILES_PATTERN, mapOf("serverId" to serverId.value))
            ?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun qualityProfileEditorRoute(serverId: ServerId, profileId: Int?): ArrRoute.Feature? =
        fillPattern(
            QUALITY_PROFILE_EDITOR_PATTERN,
            mapOf("serverId" to serverId.value, "profileId" to (profileId?.toString() ?: "new")),
        )?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun languageProfilesRoute(serverId: ServerId): ArrRoute.Feature? =
        fillPattern(LANGUAGE_PROFILES_PATTERN, mapOf("serverId" to serverId.value))
            ?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun languageProfileEditorRoute(serverId: ServerId, profileId: Int?): ArrRoute.Feature? =
        fillPattern(
            LANGUAGE_PROFILE_EDITOR_PATTERN,
            mapOf("serverId" to serverId.value, "profileId" to (profileId?.toString() ?: "new")),
        )?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun customFormatsRoute(serverId: ServerId): ArrRoute.Feature? =
        fillPattern(CUSTOM_FORMATS_PATTERN, mapOf("serverId" to serverId.value))
            ?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun customFormatEditorRoute(serverId: ServerId, formatId: Int?): ArrRoute.Feature? =
        fillPattern(
            CUSTOM_FORMAT_EDITOR_PATTERN,
            mapOf("serverId" to serverId.value, "formatId" to (formatId?.toString() ?: "new")),
        )?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun delayProfilesRoute(serverId: ServerId): ArrRoute.Feature? =
        fillPattern(DELAY_PROFILES_PATTERN, mapOf("serverId" to serverId.value))
            ?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun releaseProfilesRoute(serverId: ServerId): ArrRoute.Feature? =
        fillPattern(RELEASE_PROFILES_PATTERN, mapOf("serverId" to serverId.value))
            ?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun languagesRoute(serverId: ServerId): ArrRoute.Feature? =
        fillPattern(LANGUAGES_PATTERN, mapOf("serverId" to serverId.value))
            ?.let { ArrRoute.Feature(ROUTE_OWNER, it) }

    fun openQualityDefinitions(navigator: ArrShellNavigator, serverId: ServerId) {
        qualityDefinitionsRoute(serverId)?.let(navigator::open)
    }

    fun openQualityProfiles(navigator: ArrShellNavigator, serverId: ServerId) {
        qualityProfilesRoute(serverId)?.let(navigator::open)
    }

    fun openQualityProfileEditor(navigator: ArrShellNavigator, serverId: ServerId, profileId: Int?) {
        qualityProfileEditorRoute(serverId, profileId)?.let(navigator::open)
    }

    fun openLanguageProfiles(navigator: ArrShellNavigator, serverId: ServerId) {
        languageProfilesRoute(serverId)?.let(navigator::open)
    }

    fun openLanguageProfileEditor(navigator: ArrShellNavigator, serverId: ServerId, profileId: Int?) {
        languageProfileEditorRoute(serverId, profileId)?.let(navigator::open)
    }

    fun openCustomFormats(navigator: ArrShellNavigator, serverId: ServerId) {
        customFormatsRoute(serverId)?.let(navigator::open)
    }

    fun openCustomFormatEditor(navigator: ArrShellNavigator, serverId: ServerId, formatId: Int?) {
        customFormatEditorRoute(serverId, formatId)?.let(navigator::open)
    }

    fun openDelayProfiles(navigator: ArrShellNavigator, serverId: ServerId) {
        delayProfilesRoute(serverId)?.let(navigator::open)
    }

    fun openReleaseProfiles(navigator: ArrShellNavigator, serverId: ServerId) {
        releaseProfilesRoute(serverId)?.let(navigator::open)
    }

    fun openLanguages(navigator: ArrShellNavigator, serverId: ServerId) {
        languagesRoute(serverId)?.let(navigator::open)
    }
}

/**
 * Everything one server's quality screens need: the offline store and
 * action queue from the data graph plus the Retrofit interfaces built by
 * the composition root from [SonarrSettingsQualityConnection].
 */
class SonarrQualityClients(
    val store: EntityStore,
    val actionQueue: ActionQueue,
    val apis: SonarrQualityApis,
)

/**
 * Shell content for this group's nested routes. Settings screens do not
 * subscribe to live events: they refresh through the TTL and on
 * pull-to-refresh, so no refresh signals are plumbed here.
 */
fun sonarrSettingsQualityShellContent(
    clients: (ServerId) -> SonarrQualityClients,
    serverNames: (ServerId) -> String? = { null },
): ArrShellContent = ArrShellContent(
    feature = { route ->
        SonarrSettingsQualityFeatureSlot(
            route = route,
            clients = clients,
            serverNames = serverNames,
        )
    },
)

/**
 * Feature-slot entry: renders this group's nested routes and an empty state
 * for anything else, mirroring the media management feature's slot so a
 * partial integration never shows a blank screen.
 */
@Composable
fun SonarrSettingsQualityFeatureSlot(
    route: ArrRoute.Feature,
    clients: (ServerId) -> SonarrQualityClients,
    serverNames: (ServerId) -> String? = { null },
    modifier: Modifier = Modifier,
) {
    if (route.owner != SonarrSettingsQualityFeature.ROUTE_OWNER) {
        ForeignOwnerPlaceholder(route.owner, modifier)
        return
    }
    val segments = route.path.split('/')
    val navigator = LocalArrShellNavigator.current

    val definitions = matchRoutePattern(SonarrSettingsQualityFeature.QUALITY_DEFINITIONS_PATTERN, segments)
    val profiles = matchRoutePattern(SonarrSettingsQualityFeature.QUALITY_PROFILES_PATTERN, segments)
    val profileEditor =
        matchRoutePattern(SonarrSettingsQualityFeature.QUALITY_PROFILE_EDITOR_PATTERN, segments)
    val languageProfiles =
        matchRoutePattern(SonarrSettingsQualityFeature.LANGUAGE_PROFILES_PATTERN, segments)
    val languageProfileEditor =
        matchRoutePattern(SonarrSettingsQualityFeature.LANGUAGE_PROFILE_EDITOR_PATTERN, segments)
    val formats = matchRoutePattern(SonarrSettingsQualityFeature.CUSTOM_FORMATS_PATTERN, segments)
    val formatEditor =
        matchRoutePattern(SonarrSettingsQualityFeature.CUSTOM_FORMAT_EDITOR_PATTERN, segments)
    val delays = matchRoutePattern(SonarrSettingsQualityFeature.DELAY_PROFILES_PATTERN, segments)
    val releases = matchRoutePattern(SonarrSettingsQualityFeature.RELEASE_PROFILES_PATTERN, segments)
    val languages = matchRoutePattern(SonarrSettingsQualityFeature.LANGUAGES_PATTERN, segments)

    when {
        definitions != null -> {
            val serverId = ServerId(definitions.getValue("serverId"))
            SonarrQualityDefinitionsDestination(
                serverId = serverId,
                clients = clients(serverId),
                serverName = serverNames(serverId),
                onBack = { navigator.back() },
                modifier = modifier,
            )
        }

        profiles != null -> {
            val serverId = ServerId(profiles.getValue("serverId"))
            SonarrQualityProfilesDestination(
                serverId = serverId,
                clients = clients(serverId),
                serverName = serverNames(serverId),
                onBack = { navigator.back() },
                onOpenProfile = { profileId ->
                    SonarrSettingsQualityFeature.openQualityProfileEditor(navigator, serverId, profileId)
                },
                modifier = modifier,
            )
        }

        profileEditor != null -> {
            val serverId = ServerId(profileEditor.getValue("serverId"))
            SonarrQualityProfileEditorDestination(
                serverId = serverId,
                profileId = profileEditor.getValue("profileId").toIntOrNull(),
                clients = clients(serverId),
                serverName = serverNames(serverId),
                onBack = { navigator.back() },
                modifier = modifier,
            )
        }

        languageProfiles != null -> {
            val serverId = ServerId(languageProfiles.getValue("serverId"))
            SonarrLanguageProfilesDestination(
                serverId = serverId,
                clients = clients(serverId),
                serverName = serverNames(serverId),
                onBack = { navigator.back() },
                onOpenProfile = { profileId ->
                    SonarrSettingsQualityFeature.openLanguageProfileEditor(navigator, serverId, profileId)
                },
                modifier = modifier,
            )
        }

        languageProfileEditor != null -> {
            val serverId = ServerId(languageProfileEditor.getValue("serverId"))
            SonarrLanguageProfileEditorDestination(
                serverId = serverId,
                profileId = languageProfileEditor.getValue("profileId").toIntOrNull(),
                clients = clients(serverId),
                serverName = serverNames(serverId),
                onBack = { navigator.back() },
                modifier = modifier,
            )
        }

        formats != null -> {
            val serverId = ServerId(formats.getValue("serverId"))
            SonarrCustomFormatsDestination(
                serverId = serverId,
                clients = clients(serverId),
                serverName = serverNames(serverId),
                onBack = { navigator.back() },
                onOpenFormat = { formatId ->
                    SonarrSettingsQualityFeature.openCustomFormatEditor(navigator, serverId, formatId)
                },
                modifier = modifier,
            )
        }

        formatEditor != null -> {
            val serverId = ServerId(formatEditor.getValue("serverId"))
            SonarrCustomFormatEditorDestination(
                serverId = serverId,
                formatId = formatEditor.getValue("formatId").toIntOrNull(),
                clients = clients(serverId),
                serverName = serverNames(serverId),
                onBack = { navigator.back() },
                modifier = modifier,
            )
        }

        delays != null -> {
            val serverId = ServerId(delays.getValue("serverId"))
            SonarrDelayProfilesDestination(
                serverId = serverId,
                clients = clients(serverId),
                serverName = serverNames(serverId),
                onBack = { navigator.back() },
                modifier = modifier,
            )
        }

        releases != null -> {
            val serverId = ServerId(releases.getValue("serverId"))
            SonarrReleaseProfilesDestination(
                serverId = serverId,
                clients = clients(serverId),
                serverName = serverNames(serverId),
                onBack = { navigator.back() },
                modifier = modifier,
            )
        }

        languages != null -> {
            val serverId = ServerId(languages.getValue("serverId"))
            SonarrLanguagesDestination(
                serverId = serverId,
                clients = clients(serverId),
                serverName = serverNames(serverId),
                onBack = { navigator.back() },
                modifier = modifier,
            )
        }

        else -> ForeignOwnerPlaceholder(route.owner, modifier)
    }
}

@Composable
private fun ForeignOwnerPlaceholder(owner: String, modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(dev.appolon.homelab.arr.ui.R.string.arr_shell_unavailable_title),
        message = stringResource(dev.appolon.homelab.arr.ui.R.string.arr_shell_unavailable_message, owner),
        modifier = modifier,
    )
}
