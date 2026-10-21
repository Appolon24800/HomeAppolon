package dev.appolon.homelab.arr.api.sonarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire values defined by the Sonarr EpisodeTitleRequiredType schema. */
@Serializable
enum class EpisodeTitleRequiredType(val wireValue: String) {
    @SerialName("always")
    ALWAYS("always"),

    @SerialName("bulkSeasonReleases")
    BULK_SEASON_RELEASES("bulkSeasonReleases"),

    @SerialName("never")
    NEVER("never");

    override fun toString(): String = wireValue
}
