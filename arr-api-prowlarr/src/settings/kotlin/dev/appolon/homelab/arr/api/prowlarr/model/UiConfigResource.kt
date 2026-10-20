package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.Serializable

/**
 * Settings singleton behind the UiConfig tag: date and time formats the
 * client should mirror when rendering server data. The server always serves
 * the row with id 1.
 */
@Serializable
data class UiConfigResource(
    val id: Int = 1,
    val firstDayOfWeek: Int = 0,
    val calendarWeekColumnHeader: String? = null,
    val shortDateFormat: String? = null,
    val longDateFormat: String? = null,
    val timeFormat: String? = null,
    val showRelativeDates: Boolean = false,
    val enableColorImpairedMode: Boolean = false,
    val uiLanguage: String? = null,
    val theme: String? = null,
)
