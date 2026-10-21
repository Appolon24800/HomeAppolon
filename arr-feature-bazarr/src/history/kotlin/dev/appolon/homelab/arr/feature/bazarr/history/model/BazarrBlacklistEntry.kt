package dev.appolon.homelab.arr.feature.bazarr.history.model

import dev.appolon.homelab.arr.feature.bazarr.episodes.model.optIntOf
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.optStringOf
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.parseTimestampMillis
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * One blacklisted subtitle from the episodes or movies blacklist feed. The
 * bundled Bazarr spec declares no response schemas, so every field defaults
 * so a missing or renamed key degrades to a blank label instead of a crash,
 * and the row is [Serializable] so it persists through the offline
 * [dev.appolon.homelab.arr.data.EntityStore].
 */
@Serializable
data class BazarrBlacklistEntry(
    val provider: String = "",
    val subsId: String = "",
    val languageCode2: String = "",
    val languageName: String = "",
    val path: String = "",
    val title: String = "",
    val seriesTitle: String = "",
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeId: Int? = null,
    val radarrId: Int? = null,
    val timestampMillis: Long = 0,
    val isMovie: Boolean = false,
) {
    /** Dedupe identity matching the provider + subs_id the remove call needs. */
    val key: String get() = "$provider|$subsId"

    /** Media label: "Series S01E02 Title" or the movie title. */
    val mediaLabel: String
        get() = if (isMovie) {
            title
        } else {
            listOfNotNull(
                seriesTitle.ifBlank { null },
                seasonNumber?.let { s ->
                    episodeNumber?.let { e ->
                        String.format(java.util.Locale.ROOT, "S%02dE%02d", s, e)
                    }
                },
                title.ifBlank { null },
            ).joinToString(" ")
        }
}

/**
 * Parses one blacklist row. The language arrives either as an object like
 * `{"code2": "en", "name": "English"}` or as a bare code, matching the
 * history feeds. Rows without both a provider and a subs id cannot be
 * removed, so they are dropped.
 */
internal fun JsonObject.toBlacklistEntry(isMovie: Boolean): BazarrBlacklistEntry? {
    val subsId = optStringOf("subs_id", "subsId", "id")
    val provider = optStringOf("provider")
    if (subsId.isBlank() || provider.isBlank()) return null
    val languageObject = this["language"] as? JsonObject
    val languageName = languageObject?.optStringOf("name", "title").orEmpty()
    val languageCode = when (val raw = this["language"]) {
        is JsonPrimitive -> raw.content
        else -> languageObject?.optStringOf("code2", "code").orEmpty()
    }
    return BazarrBlacklistEntry(
        provider = provider,
        subsId = subsId,
        languageCode2 = languageCode,
        languageName = languageName,
        path = optStringOf("subtitles_path", "path"),
        title = optStringOf("title", "movieTitle"),
        seriesTitle = optStringOf("seriesTitle", "series_title"),
        seasonNumber = optIntOf("seasonNumber", "season"),
        episodeNumber = optIntOf("episodeNumber", "episode"),
        episodeId = optIntOf("episodeId", "episode_id"),
        radarrId = optIntOf("radarrId", "radarr_id"),
        timestampMillis = parseTimestampMillis(),
        isMovie = isMovie,
    )
}
