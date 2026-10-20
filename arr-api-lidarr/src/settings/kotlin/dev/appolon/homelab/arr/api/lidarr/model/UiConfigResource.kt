package dev.appolon.homelab.arr.api.lidarr.model

import kotlinx.serialization.Serializable

/** Lidarr UiConfigResource wire resource for UI date and language settings. */
@Serializable
data class UiConfigResource(
    val id: Int = 0,
    val firstDayOfWeek: Int = 0,
    val calendarWeekColumnHeader: String? = null,
    val shortDateFormat: String? = null,
    val longDateFormat: String? = null,
    val timeFormat: String? = null,
    val showRelativeDates: Boolean = false,
    val enableColorImpairedMode: Boolean = false,
    val uiLanguage: Int = 0,
    val expandAlbumByDefault: Boolean = false,
    val expandSingleByDefault: Boolean = false,
    val expandEPByDefault: Boolean = false,
    val expandBroadcastByDefault: Boolean = false,
    val expandOtherByDefault: Boolean = false,
    val theme: String? = null,
)
