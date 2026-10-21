package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.Serializable

/** Sonarr UiConfigResource wire contract. */
@Serializable
data class UiConfigResource(
    val id: Int = 0,
    val firstDayOfWeek: Int = 0,
    val calendarWeekColumnHeader: String? = null,
    val shortDateFormat: String? = null,
    val longDateFormat: String? = null,
    val timeFormat: String? = null,
    val timeZone: String? = null,
    val showRelativeDates: Boolean = false,
    val enableColorImpairedMode: Boolean = false,
    val theme: String? = null,
    val uiLanguage: Int = 0
)
