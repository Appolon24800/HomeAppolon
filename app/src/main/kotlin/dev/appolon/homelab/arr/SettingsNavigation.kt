package dev.appolon.homelab.arr

import dev.appolon.homelab.R as AppR
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.nav.fillPattern
import dev.appolon.homelab.arr.ui.R as UiR
import dev.appolon.homelab.arr.feature.radarr.settings.mm.RadarrMediaManagementFeature
import dev.appolon.homelab.arr.feature.radarr.settings.quality.RadarrSettingsQualityFeature
import dev.appolon.homelab.arr.feature.radarr.settings.connect.RadarrSettingsConnectFeature
import dev.appolon.homelab.arr.feature.radarr.settings.system.RadarrSettingsSystemFeature
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.SonarrMediaManagementFeature
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.SonarrSettingsQualityFeature
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.SonarrSettingsConnectFeature
import dev.appolon.homelab.arr.feature.sonarr.settings.system.SonarrSettingsSystemFeature
import dev.appolon.homelab.arr.feature.lidarr.settings.mm.LidarrMediaManagementFeature
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.LidarrSettingsQualityFeature
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.LidarrSettingsConnectFeature
import dev.appolon.homelab.arr.feature.lidarr.settings.system.LidarrSettingsSystemFeature
import dev.appolon.homelab.arr.feature.prowlarr.settings.ProwlarrSettingsFeature
import dev.appolon.homelab.arr.feature.bazarr.settings.BazarrSettingsFeature

internal data class SettingsEntry(val title: Int, val route: ArrRoute.Feature)
internal data class SettingsSection(val title: Int, val entries: List<SettingsEntry>)

/** Top-level settings pages. Editors remain reachable through their parent lists. */
internal fun settingsSections(app: AppType, id: ServerId): List<SettingsSection> {
    fun section(title: Int, vararg entries: Pair<Int, ArrRoute.Feature?>) = SettingsSection(
        title,
        entries.mapNotNull { (label, route) -> route?.let { SettingsEntry(label, it) } },
    )
    return when (app) {
        AppType.RADARR -> listOf(
            section(AppR.string.arr_home_library,
                UiR.string.arr_common_mm_title to RadarrMediaManagementFeature.mediaManagementRoute(id),
                dev.appolon.homelab.arr.feature.radarr.R.string.arr_radarr_mm_link_naming to RadarrMediaManagementFeature.namingRoute(id),
                UiR.string.arr_common_mm_link_root_folders to RadarrMediaManagementFeature.rootFoldersRoute(id),
            ),
            section(UiR.string.arr_common_quality,
                UiR.string.arr_common_quality_definitions_title to RadarrSettingsQualityFeature.qualityDefinitionsRoute(id),
                UiR.string.arr_common_quality_profiles_title to RadarrSettingsQualityFeature.qualityProfilesRoute(id),
                UiR.string.arr_common_quality_custom_formats_title to RadarrSettingsQualityFeature.customFormatsRoute(id),
                UiR.string.arr_common_quality_delay_title to RadarrSettingsQualityFeature.delayProfilesRoute(id),
                UiR.string.arr_common_quality_release_title to RadarrSettingsQualityFeature.releaseProfilesRoute(id),
                UiR.string.arr_common_quality_languages_title to RadarrSettingsQualityFeature.languagesRoute(id),
            ),
            section(AppR.string.arr_settings_connections,
                UiR.string.arr_common_indexers_title to RadarrSettingsConnectFeature.indexersRoute(id),
                UiR.string.arr_common_clients_title to RadarrSettingsConnectFeature.downloadClientsRoute(id),
                UiR.string.arr_common_paths_title to RadarrSettingsConnectFeature.remotePathMappingsRoute(id),
                UiR.string.arr_common_lists_title to RadarrSettingsConnectFeature.importListsRoute(id),
                UiR.string.arr_common_exclusions_title to RadarrSettingsConnectFeature.importListExclusionsRoute(id),
                UiR.string.arr_common_notifications_title to RadarrSettingsConnectFeature.notificationsRoute(id),
                UiR.string.arr_common_metadata_title to RadarrSettingsConnectFeature.metadataRoute(id),
            ),
            section(AppR.string.arr_settings_system,
                UiR.string.arr_common_general_title to RadarrSettingsSystemFeature.generalRoute(id),
                UiR.string.arr_common_tags_title to RadarrSettingsSystemFeature.tagsRoute(id),
                UiR.string.arr_common_autotag_title to RadarrSettingsSystemFeature.autoTaggingRoute(id),
                UiR.string.arr_common_backup_title to RadarrSettingsSystemFeature.backupRoute(id),
                UiR.string.arr_common_updates_title to RadarrSettingsSystemFeature.updatesRoute(id),
                UiR.string.arr_common_status_title to RadarrSettingsSystemFeature.systemStatusRoute(id),
            ),
        )
        AppType.SONARR -> listOf(
            section(AppR.string.arr_home_library,
                UiR.string.arr_common_mm_title to SonarrMediaManagementFeature.mediaManagementRoute(id),
                dev.appolon.homelab.arr.feature.sonarr.R.string.arr_sonarr_mm_link_naming to SonarrMediaManagementFeature.namingRoute(id),
                UiR.string.arr_common_mm_link_root_folders to SonarrMediaManagementFeature.rootFoldersRoute(id),
            ),
            section(UiR.string.arr_common_quality,
                UiR.string.arr_common_quality_definitions_title to SonarrSettingsQualityFeature.qualityDefinitionsRoute(id),
                UiR.string.arr_common_quality_profiles_title to SonarrSettingsQualityFeature.qualityProfilesRoute(id),
                dev.appolon.homelab.arr.feature.sonarr.R.string.arr_sonarr_quality_language_profiles_title to SonarrSettingsQualityFeature.languageProfilesRoute(id),
                UiR.string.arr_common_quality_custom_formats_title to SonarrSettingsQualityFeature.customFormatsRoute(id),
                UiR.string.arr_common_quality_delay_title to SonarrSettingsQualityFeature.delayProfilesRoute(id),
                UiR.string.arr_common_quality_release_title to SonarrSettingsQualityFeature.releaseProfilesRoute(id),
                UiR.string.arr_common_quality_languages_title to SonarrSettingsQualityFeature.languagesRoute(id),
            ),
            section(AppR.string.arr_settings_connections,
                UiR.string.arr_common_indexers_title to SonarrSettingsConnectFeature.indexersRoute(id),
                UiR.string.arr_common_clients_title to SonarrSettingsConnectFeature.downloadClientsRoute(id),
                UiR.string.arr_common_paths_title to SonarrSettingsConnectFeature.remotePathMappingsRoute(id),
                UiR.string.arr_common_lists_title to SonarrSettingsConnectFeature.importListsRoute(id),
                UiR.string.arr_common_exclusions_title to SonarrSettingsConnectFeature.importListExclusionsRoute(id),
                UiR.string.arr_common_notifications_title to SonarrSettingsConnectFeature.notificationsRoute(id),
                UiR.string.arr_common_metadata_title to SonarrSettingsConnectFeature.metadataRoute(id),
            ),
            section(AppR.string.arr_settings_system,
                UiR.string.arr_common_general_title to SonarrSettingsSystemFeature.generalRoute(id),
                UiR.string.arr_common_tags_title to SonarrSettingsSystemFeature.tagsRoute(id),
                UiR.string.arr_common_autotag_title to SonarrSettingsSystemFeature.autoTaggingRoute(id),
                UiR.string.arr_common_backup_title to SonarrSettingsSystemFeature.backupRoute(id),
                UiR.string.arr_common_updates_title to SonarrSettingsSystemFeature.updatesRoute(id),
                UiR.string.arr_common_status_title to SonarrSettingsSystemFeature.systemStatusRoute(id),
            ),
        )
        AppType.LIDARR -> listOf(
            section(AppR.string.arr_home_library,
                UiR.string.arr_common_mm_title to LidarrMediaManagementFeature.mediaManagementRoute(id),
                dev.appolon.homelab.arr.feature.lidarr.R.string.arr_lidarr_mm_link_naming to LidarrMediaManagementFeature.namingRoute(id),
                UiR.string.arr_common_mm_link_root_folders to LidarrMediaManagementFeature.rootFoldersRoute(id),
            ),
            section(UiR.string.arr_common_quality,
                UiR.string.arr_common_quality_definitions_title to LidarrSettingsQualityFeature.qualityDefinitionsRoute(id),
                UiR.string.arr_common_quality_profiles_title to LidarrSettingsQualityFeature.qualityProfilesRoute(id),
                dev.appolon.homelab.arr.feature.lidarr.R.string.arr_lidarr_quality_metadata_profiles_title to LidarrSettingsQualityFeature.metadataProfilesRoute(id),
                dev.appolon.homelab.arr.feature.lidarr.R.string.arr_lidarr_quality_metadata_provider_title to LidarrSettingsQualityFeature.metadataProviderRoute(id),
                UiR.string.arr_common_quality_custom_formats_title to LidarrSettingsQualityFeature.customFormatsRoute(id),
                UiR.string.arr_common_quality_delay_title to LidarrSettingsQualityFeature.delayProfilesRoute(id),
                UiR.string.arr_common_quality_release_title to LidarrSettingsQualityFeature.releaseProfilesRoute(id),
                UiR.string.arr_common_quality_languages_title to LidarrSettingsQualityFeature.languagesRoute(id),
            ),
            section(AppR.string.arr_settings_connections,
                UiR.string.arr_common_indexers_title to LidarrSettingsConnectFeature.indexersRoute(id),
                UiR.string.arr_common_clients_title to LidarrSettingsConnectFeature.downloadClientsRoute(id),
                UiR.string.arr_common_paths_title to LidarrSettingsConnectFeature.remotePathMappingsRoute(id),
                UiR.string.arr_common_lists_title to LidarrSettingsConnectFeature.importListsRoute(id),
                UiR.string.arr_common_exclusions_title to LidarrSettingsConnectFeature.importListExclusionsRoute(id),
                UiR.string.arr_common_notifications_title to LidarrSettingsConnectFeature.notificationsRoute(id),
                UiR.string.arr_common_metadata_title to LidarrSettingsConnectFeature.metadataRoute(id),
            ),
            section(AppR.string.arr_settings_system,
                UiR.string.arr_common_general_title to LidarrSettingsSystemFeature.generalRoute(id),
                UiR.string.arr_common_tags_title to LidarrSettingsSystemFeature.tagsRoute(id),
                UiR.string.arr_common_autotag_title to LidarrSettingsSystemFeature.autoTaggingRoute(id),
                UiR.string.arr_common_backup_title to LidarrSettingsSystemFeature.backupRoute(id),
                UiR.string.arr_common_updates_title to LidarrSettingsSystemFeature.updatesRoute(id),
                UiR.string.arr_common_status_title to LidarrSettingsSystemFeature.systemStatusRoute(id),
            ),
        )
        AppType.PROWLARR -> listOf(
            section(UiR.string.arr_common_general_title,
                UiR.string.arr_common_clients_title to ProwlarrSettingsFeature.downloadClientsRoute(id),
                UiR.string.arr_common_notifications_title to ProwlarrSettingsFeature.notificationsRoute(id),
                dev.appolon.homelab.arr.feature.prowlarr.R.string.arr_prowlarr_settings_proxy_note_title to ProwlarrSettingsFeature.indexerProxiesRoute(id),
                UiR.string.arr_common_general_title to ProwlarrSettingsFeature.generalRoute(id),
                UiR.string.arr_common_status_title to ProwlarrSettingsFeature.systemRoute(id),
            ),
        )
        AppType.BAZARR -> listOf(
            section(dev.appolon.homelab.arr.feature.bazarr.R.string.arr_bazarr_settings_title,
                dev.appolon.homelab.arr.feature.bazarr.R.string.arr_bazarr_settings_title to
                    fillPattern(BazarrSettingsFeature.HOME_PATTERN, mapOf("serverId" to id.value))
                        ?.let { ArrRoute.Feature(BazarrSettingsFeature.ROUTE_OWNER, it) },
            ),
        )
    }
}
