package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.Serializable

/** Wire contract for Radarr UiConfigResource. Dates remain unparsed strings. */
@Serializable
data class UiConfigResource(
    val id: Int = 0,
    val firstDayOfWeek: Int = 0,
    val calendarWeekColumnHeader: String? = null,
    val movieRuntimeFormat: MovieRuntimeFormatType? = null,
    val shortDateFormat: String? = null,
    val longDateFormat: String? = null,
    val timeFormat: String? = null,
    val showRelativeDates: Boolean = false,
    val enableColorImpairedMode: Boolean = false,
    val movieInfoLanguage: Int = 0,
    val uiLanguage: Int = 0,
    val theme: String? = null,
)
