package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.Serializable

/** One selectable UI language, from the localization options endpoint. */
@Serializable
data class LocalizationOption(
    val name: String? = null,
    val value: String? = null,
)
