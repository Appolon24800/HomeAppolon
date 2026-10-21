package dev.appolon.homelab.arr.feature.bazarr.series.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Subtitle completeness of one series, movie, or episode, derived from the
 * count and missing-list fields the Bazarr API reports. The web UI shows the
 * same four states with its progress coloring.
 */
enum class SubtitleStatus {
    /** Nothing wanted: no profile, or no tracked episodes at all. */
    NONE_WANTED,

    /** Every wanted language is present. */
    COMPLETE,

    /** Some wanted languages are present, some are missing. */
    PARTIAL,

    /** Nothing present, everything wanted is missing. */
    MISSING,
}

/** One language or subtitle track as Bazarr reports it (code2, display name, flags). */
@Serializable
data class BazarrLanguage(
    val code2: String,
    val name: String,
    val forced: Boolean = false,
    val hearingImpaired: Boolean = false,
) {
    /** Label shown where space is tight: the display name when known, else the code. */
    val label: String get() = name.ifBlank { code2 }
}

/**
 * One series of the Bazarr series list, parsed from the spec-undocumented
 * `GET series` payload. Counts follow the Bazarr meaning: [episodeFileCount]
 * is every episode tracked for the series, [episodeMissingCount] those with a
 * non-empty missing list, so complete episodes are the difference.
 */
@Serializable
data class BazarrSeries(
    val seriesId: Int,
    val title: String,
    val path: String = "",
    val year: String? = null,
    val poster: String? = null,
    val fanart: String? = null,
    val overview: String? = null,
    val imdbId: String? = null,
    val tvdbId: Long? = null,
    val monitored: Boolean = false,
    val ended: Boolean? = null,
    val seriesType: String? = null,
    val audioLanguages: List<BazarrLanguage> = emptyList(),
    val tags: List<String> = emptyList(),
    val profileId: Int? = null,
    val episodeFileCount: Int = 0,
    val episodeMissingCount: Int = 0,
    val status: SubtitleStatus = SubtitleStatus.NONE_WANTED,
) {
    /** Episodes whose wanted subtitles are all present. */
    val completeEpisodeCount: Int get() = (episodeFileCount - episodeMissingCount).coerceIn(0, episodeFileCount)

    /** Sort and display year; Bazarr hands years over as strings. */
    val yearValue: Int? get() = year?.trim()?.toIntOrNull()
}

/**
 * One movie of the Bazarr movies list. [subtitles] holds the tracks on disk,
 * [missingSubtitles] the wanted-but-absent languages; both together drive the
 * status badge.
 */
@Serializable
data class BazarrMovie(
    val radarrId: Int,
    val title: String,
    val path: String = "",
    val year: String? = null,
    val poster: String? = null,
    val fanart: String? = null,
    val overview: String? = null,
    val imdbId: String? = null,
    val tmdbId: Long? = null,
    val monitored: Boolean = false,
    val audioLanguages: List<BazarrLanguage> = emptyList(),
    val subtitles: List<BazarrLanguage> = emptyList(),
    val missingSubtitles: List<BazarrLanguage> = emptyList(),
    val tags: List<String> = emptyList(),
    val profileId: Int? = null,
    val status: SubtitleStatus = SubtitleStatus.NONE_WANTED,
) {
    val yearValue: Int? get() = year?.trim()?.toIntOrNull()
}

/** One episode inside a series, with its own subtitle state. */
@Serializable
data class BazarrEpisode(
    val episodeId: Int,
    val seriesId: Int,
    val season: Int,
    val episode: Int,
    val title: String,
    val path: String? = null,
    val monitored: Boolean = false,
    val audioLanguages: List<BazarrLanguage> = emptyList(),
    val subtitles: List<BazarrLanguage> = emptyList(),
    val missingSubtitles: List<BazarrLanguage> = emptyList(),
    val status: SubtitleStatus = SubtitleStatus.NONE_WANTED,
) {
    /** Season-episode code the UI shows, for example "S02E05". */
    val code: String get() = "S%02dE%02d".format(season, episode)
}

/**
 * One Bazarr language profile, the mass editor's assignment target. Profile
 * payloads are spec-undocumented, so ids and names carry fallbacks and the
 * wanted languages degrade to an empty list instead of failing a load.
 */
@Serializable
data class BazarrLanguageProfile(
    val profileId: Int,
    val name: String,
    val cutoff: Int? = null,
    /** code2 of every wanted language, used by the monolingual filter. */
    val languages: List<String> = emptyList(),
) {
    /** Display label with a stable fallback when the server sent no name. */
    val label: String get() = name.ifBlank { "#$profileId" }
}

/** One blacklisted subtitle provider entry, used for the blacklist filter. */
@Serializable
data class BazarrBlacklistEntry(
    val provider: String,
    val languageName: String? = null,
    val seriesId: Int? = null,
    val radarrId: Int? = null,
    val title: String? = null,
    val episodeNumber: String? = null,
    val timestamp: String? = null,
)

/** Global missing counts from `GET badges`, shown as the list header summary. */
@Serializable
data class BazarrBadges(
    val episodesMissing: Int = 0,
    val moviesMissing: Int = 0,
    val providers: Int = 0,
    val healthIssues: Int = 0,
)

/**
 * Status math shared by every Bazarr list screen. Pure and tested directly;
 * the parsers call it while shaping items.
 */
object SubtitleStatuses {

    /**
     * Series status from the episode counts. A null or zero profile means
     * Bazarr tracks no wanted language for the series, and a series without
     * tracked episodes has nothing to subtitle either.
     */
    fun ofSeries(profileId: Int?, totalEpisodes: Int, missingEpisodes: Int): SubtitleStatus {
        if (profileId == null || profileId == 0 || totalEpisodes <= 0) return SubtitleStatus.NONE_WANTED
        val missing = missingEpisodes.coerceIn(0, totalEpisodes)
        return when {
            missing == 0 -> SubtitleStatus.COMPLETE
            missing >= totalEpisodes -> SubtitleStatus.MISSING
            else -> SubtitleStatus.PARTIAL
        }
    }

    /** Movie and episode status from the present and missing language lists. */
    fun ofUnit(present: List<BazarrLanguage>, missing: List<BazarrLanguage>): SubtitleStatus = when {
        missing.isEmpty() && present.isEmpty() -> SubtitleStatus.NONE_WANTED
        missing.isEmpty() -> SubtitleStatus.COMPLETE
        present.isEmpty() -> SubtitleStatus.MISSING
        else -> SubtitleStatus.PARTIAL
    }
}

/**
 * Defensive readers for the Bazarr endpoints whose bundled spec declares no
 * response schema. Every accessor tolerates absent, null, or differently
 * typed keys, so one odd row can never fail a whole list load. Envelopes:
 * series and movies answer `{data, total}`, episodes and blacklists answer
 * `{data}`, profiles and badges answer without an envelope.
 */
object BazarrParsers {

    /** Series list out of the `GET series` envelope; missing data yields an empty list. */
    fun parseSeries(body: JsonObject): List<BazarrSeries> =
        body.array("data").orEmpty().mapNotNull { element ->
            (element as? JsonObject)?.let(::parseSeriesItem)
        }

    /** Movie list out of the `GET movies` envelope. */
    fun parseMovies(body: JsonObject): List<BazarrMovie> =
        body.array("data").orEmpty().mapNotNull { element ->
            (element as? JsonObject)?.let(::parseMovieItem)
        }

    /** Episode list out of the `GET episodes` envelope. */
    fun parseEpisodes(body: JsonObject): List<BazarrEpisode> =
        body.array("data").orEmpty().mapNotNull { element ->
            (element as? JsonObject)?.let(::parseEpisodeItem)
        }

    /**
     * Language profiles from `GET system/languages/profiles`. Accepts the
     * plain-array shape the Bazarr frontend types describe even though the
     * committed Retrofit interface types the body as [JsonObject]: callers
     * hand the parsed payload through, and a shape mismatch degrades to an
     * empty list rather than an error.
     */
    fun parseProfiles(body: JsonObject): List<BazarrLanguageProfile> =
        body.array("data").orEmpty().mapNotNull { element ->
            (element as? JsonObject)?.let(::parseProfileItem)
        }

    /** Episode blacklist entries carrying the series ids the filter joins on. */
    fun parseEpisodeBlacklist(body: JsonObject): List<BazarrBlacklistEntry> =
        body.array("data").orEmpty().mapNotNull { element ->
            (element as? JsonObject)?.let(::parseBlacklistItem)
        }

    /** Movie blacklist entries carrying the radarr ids the filter joins on. */
    fun parseMovieBlacklist(body: JsonObject): List<BazarrBlacklistEntry> =
        body.array("data").orEmpty().mapNotNull { element ->
            (element as? JsonObject)?.let(::parseBlacklistItem)
        }

    /** Badge counts; Bazarr reports flat missing counts per media kind. */
    fun parseBadges(body: JsonObject): BazarrBadges = BazarrBadges(
        episodesMissing = body.int("episodes") ?: 0,
        moviesMissing = body.int("movies") ?: 0,
        providers = body.int("providers") ?: 0,
        healthIssues = body.int("status") ?: 0,
    )

    /** Serializer for storing one series' episode list as a single entity row. */
    val episodeListSerializer = ListSerializer(BazarrEpisode.serializer())

    private fun parseSeriesItem(row: JsonObject): BazarrSeries? {
        val id = row.int("sonarrSeriesId") ?: return null
        val total = row.int("episodeFileCount") ?: 0
        val missing = row.int("episodeMissingCount") ?: 0
        val profileId = row.int("profileId")
        return BazarrSeries(
            seriesId = id,
            title = row.string("title").orEmpty(),
            path = row.string("path").orEmpty(),
            year = row.string("year"),
            poster = row.string("poster"),
            fanart = row.string("fanart"),
            overview = row.string("overview"),
            imdbId = row.string("imdbId"),
            tvdbId = row.long("tvdbId"),
            monitored = row.bool("monitored") ?: false,
            ended = row.bool("ended"),
            seriesType = row.string("seriesType"),
            audioLanguages = parseLanguages(row, "audio_language"),
            tags = parseStrings(row, "tags"),
            profileId = profileId,
            episodeFileCount = total,
            episodeMissingCount = missing,
            status = SubtitleStatuses.ofSeries(profileId, total, missing),
        )
    }

    private fun parseMovieItem(row: JsonObject): BazarrMovie? {
        val id = row.int("radarrId") ?: return null
        val present = parseLanguages(row, "subtitles")
        val missing = parseLanguages(row, "missing_subtitles")
        return BazarrMovie(
            radarrId = id,
            title = row.string("title").orEmpty(),
            path = row.string("path").orEmpty(),
            year = row.string("year"),
            poster = row.string("poster"),
            fanart = row.string("fanart"),
            overview = row.string("overview"),
            imdbId = row.string("imdbId"),
            tmdbId = row.long("tmdbId"),
            monitored = row.bool("monitored") ?: false,
            audioLanguages = parseLanguages(row, "audio_language"),
            subtitles = present,
            missingSubtitles = missing,
            tags = parseStrings(row, "tags"),
            profileId = row.int("profileId"),
            status = SubtitleStatuses.ofUnit(present, missing),
        )
    }

    private fun parseEpisodeItem(row: JsonObject): BazarrEpisode? {
        val episodeId = row.int("sonarrEpisodeId") ?: return null
        val seriesId = row.int("sonarrSeriesId") ?: return null
        val present = parseLanguages(row, "subtitles")
        val missing = parseLanguages(row, "missing_subtitles")
        return BazarrEpisode(
            episodeId = episodeId,
            seriesId = seriesId,
            season = row.int("season") ?: 0,
            episode = row.int("episode") ?: 0,
            title = row.string("title").orEmpty(),
            path = row.string("path"),
            monitored = row.bool("monitored") ?: false,
            audioLanguages = parseLanguages(row, "audio_language"),
            subtitles = present,
            missingSubtitles = missing,
            status = SubtitleStatuses.ofUnit(present, missing),
        )
    }

    private fun parseProfileItem(row: JsonObject): BazarrLanguageProfile? {
        val id = row.int("profileId") ?: row.int("id") ?: return null
        val languages = row.array("items").orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            when (val language = item["language"]) {
                is JsonPrimitive -> language.content
                is JsonObject -> language.string("code2")
                else -> null
            }
        }
        return BazarrLanguageProfile(
            profileId = id,
            name = row.string("name") ?: row.string("title").orEmpty(),
            cutoff = row.int("cutoff"),
            languages = languages,
        )
    }

    private fun parseBlacklistItem(row: JsonObject): BazarrBlacklistEntry? {
        val provider = row.string("provider") ?: return null
        return BazarrBlacklistEntry(
            provider = provider,
            languageName = (row["language"] as? JsonObject)?.string("name"),
            seriesId = row.int("sonarrSeriesId"),
            radarrId = row.int("radarrId"),
            title = row.string("seriesTitle") ?: row.string("title"),
            episodeNumber = row.string("episode_number"),
            timestamp = row.string("parsed_timestamp") ?: row.string("timestamp"),
        )
    }

    /** Language list under [key]; entries may be objects or bare code strings. */
    private fun parseLanguages(row: JsonObject, key: String): List<BazarrLanguage> =
        row.array(key).orEmpty().mapNotNull { element ->
            when (element) {
                is JsonObject -> parseLanguage(element)
                is JsonPrimitive -> element.content.takeIf { it.isNotBlank() }?.let { code ->
                    BazarrLanguage(code2 = code, name = code)
                }
                else -> null
            }
        }

    private fun parseLanguage(obj: JsonObject): BazarrLanguage? {
        val code = obj.string("code2") ?: obj.string("code3") ?: obj.string("code") ?: return null
        return BazarrLanguage(
            code2 = code,
            name = obj.string("name").orEmpty(),
            forced = obj.bool("forced") ?: false,
            hearingImpaired = obj.bool("hi") ?: false,
        )
    }

    private fun parseStrings(row: JsonObject, key: String): List<String> =
        row.array(key).orEmpty().mapNotNull { element ->
            (element as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        }

    private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotEmpty() }

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull
}
