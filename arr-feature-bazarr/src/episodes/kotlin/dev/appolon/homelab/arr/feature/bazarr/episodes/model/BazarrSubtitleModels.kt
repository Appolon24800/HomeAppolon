package dev.appolon.homelab.arr.feature.bazarr.episodes.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Typed view models for the Bazarr episodes and movie subtitle screens. The
 * bundled Bazarr spec declares no response schemas, so every endpoint returns
 * a raw [JsonObject]; these models are the defensive projection of those
 * payloads. Every field defaults so a missing or renamed key degrades to a
 * blank label instead of a crash, and every model is [Serializable] so rows
 * persist through the offline [dev.appolon.homelab.arr.data.EntityStore].
 */
@Serializable
data class BazarrLanguageRef(
    val code2: String = "",
    val name: String = "",
    val hearingImpaired: Boolean = false,
    val forced: Boolean = false,
)

/** One subtitle file attached to an episode or movie. */
@Serializable
data class BazarrSubtitleTrack(
    val path: String = "",
    val languageCode2: String = "",
    val languageName: String = "",
    val forced: Boolean = false,
    val hearingImpaired: Boolean = false,
    val score: Double? = null,
    val provider: String = "",
)

/** Per-episode subtitle state: what exists and what is missing. */
@Serializable
data class BazarrEpisodeSubtitles(
    val episodeId: Int,
    val seriesId: Int = 0,
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val title: String = "",
    val audioLanguages: List<BazarrLanguageRef> = emptyList(),
    val subtitles: List<BazarrSubtitleTrack> = emptyList(),
    val missingLanguages: List<BazarrLanguageRef> = emptyList(),
) {
    /** Sorting and grouping key, e.g. "S01E05". Locale-stable digits. */
    val code: String get() = String.format(java.util.Locale.ROOT, "S%02dE%02d", seasonNumber, episodeNumber)
}

/** Per-movie subtitle state: what exists and what is missing. */
@Serializable
data class BazarrMovieSubtitles(
    val radarrId: Int,
    val title: String = "",
    val year: Int? = null,
    val audioLanguages: List<BazarrLanguageRef> = emptyList(),
    val subtitles: List<BazarrSubtitleTrack> = emptyList(),
    val missingLanguages: List<BazarrLanguageRef> = emptyList(),
)

/** One wanted (missing) subtitle row for an episode. */
@Serializable
data class BazarrWantedEpisode(
    val episodeId: Int,
    val seriesId: Int = 0,
    val seriesTitle: String = "",
    val title: String = "",
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val missingLanguages: List<BazarrLanguageRef> = emptyList(),
) {
    val code: String get() = String.format(java.util.Locale.ROOT, "S%02dE%02d", seasonNumber, episodeNumber)
}

/** One wanted (missing) subtitle row for a movie. */
@Serializable
data class BazarrWantedMovie(
    val radarrId: Int,
    val title: String = "",
    val year: Int? = null,
    val missingLanguages: List<BazarrLanguageRef> = emptyList(),
)

/** One subtitle decision from the episode or movie history feed. */
@Serializable
data class BazarrHistoryEvent(
    val action: String = "",
    val description: String = "",
    val title: String = "",
    val seriesTitle: String = "",
    val languageCode2: String = "",
    val provider: String = "",
    val score: Double? = null,
    val timestampMillis: Long = 0,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeId: Int? = null,
    val radarrId: Int? = null,
    val isMovie: Boolean = false,
)

/** One provider hit from a manual subtitle search. */
@Serializable
data class BazarrProviderResult(
    val provider: String = "",
    val subtitleId: String = "",
    val languageCode2: String = "",
    val languageName: String = "",
    val hearingImpaired: Boolean = false,
    val forced: Boolean = false,
    val score: Double? = null,
    val originalFormat: Boolean = false,
    val releaseInfo: String = "",
    val upvotes: Int? = null,
)

/** One selectable language from the Bazarr languages endpoint. */
@Serializable
data class BazarrLanguageOption(val code2: String, val name: String) {
    val label: String get() = if (name.isBlank()) code2 else name
}

/*
 * Defensive JsonElement accessors. The Bazarr spec carries no schemas and the
 * server renames keys between versions, so every reader tolerates several
 * candidate names, wrong primitive kinds, and absent keys.
 */

internal fun JsonObject.optStringOf(vararg names: String): String {
    for (name in names) {
        val value = this[name] as? JsonPrimitive ?: continue
        if (value.isString) return value.content
        val asLiteral = value.contentOrNullSafe()
        if (asLiteral != null) return asLiteral
    }
    return ""
}

internal fun JsonObject.optIntOf(vararg names: String): Int? {
    for (name in names) {
        val value = this[name] as? JsonPrimitive ?: continue
        value.intOrNull?.let { return it }
        value.doubleOrNull?.let { return it.toInt() }
    }
    return null
}

internal fun JsonObject.optDoubleOf(vararg names: String): Double? {
    for (name in names) {
        val value = this[name] as? JsonPrimitive ?: continue
        value.doubleOrNull?.let { return it }
        if (value.isString) value.content.toDoubleOrNull()?.let { return it }
    }
    return null
}

/** Reads a boolean written as a JSON bool or a "True"/"false" style string. */
internal fun JsonObject.optBooleanOf(vararg names: String): Boolean? {
    for (name in names) {
        val value = this[name] as? JsonPrimitive ?: continue
        value.booleanOrNull?.let { return it }
        if (value.isString) {
            when (value.content.lowercase()) {
                "true" -> return true
                "false" -> return false
            }
        }
    }
    return null
}

internal fun JsonObject.optArrayOf(name: String): JsonArray? = this[name] as? JsonArray

/** First present member among the candidate keys; Bazarr renames these across versions. */
internal fun JsonObject.firstOf(vararg names: String): JsonElement? {
    for (name in names) {
        this[name]?.let { return it }
    }
    return null
}

private fun JsonPrimitive.contentOrNullSafe(): String? = if (isString) null else content

/**
 * Extracts the row array from a passthrough payload. Bazarr wraps list
 * endpoints in `{"data": [...], "total": n}` but some builds return the array
 * under another key or at the root of the object, so every array-valued
 * member is a candidate with "data" winning.
 */
internal fun JsonObject.rowsOf(): List<JsonObject> {
    optArrayOf("data")?.let { return it.objects() }
    for (value in values) {
        (value as? JsonArray)?.let { return it.objects() }
    }
    return emptyList()
}

internal fun JsonArray.objects(): List<JsonObject> = filterIsInstance<JsonObject>()

/** Parses one language reference: `{"code2": "en", "name": "English"}` or `"en"`. */
internal fun JsonElement.toLanguageRef(): BazarrLanguageRef? = when (this) {
    is JsonObject -> BazarrLanguageRef(
        code2 = optStringOf("code2", "code", "language"),
        name = optStringOf("name", "title"),
        hearingImpaired = optBooleanOf("hi", "hearing_impaired") ?: false,
        forced = optBooleanOf("forced") ?: false,
    )

    is JsonPrimitive -> BazarrLanguageRef(code2 = content)

    else -> null
}

internal fun JsonElement?.toLanguageList(): List<BazarrLanguageRef> =
    (this as? JsonArray)?.mapNotNull { it.toLanguageRef() }.orEmpty()

/** Parses one attached subtitle file row. */
internal fun JsonObject.toSubtitleTrack(): BazarrSubtitleTrack {
    val languageRef = (this["language"] as? JsonObject)?.toLanguageRef()
    val languageCode = when (val raw = this["language"]) {
        is JsonPrimitive -> raw.content
        else -> languageRef?.code2.orEmpty()
    }
    return BazarrSubtitleTrack(
        path = optStringOf("path"),
        languageCode2 = if (languageCode.isBlank()) languageRef?.code2.orEmpty() else languageCode,
        languageName = languageRef?.name.orEmpty(),
        forced = optBooleanOf("forced") ?: false,
        hearingImpaired = optBooleanOf("hearing_impaired", "hi") ?: false,
        score = optDoubleOf("score"),
        provider = optStringOf("provider"),
    )
}

internal fun JsonObject.toSubtitleTracks(): List<BazarrSubtitleTrack> =
    optArrayOf("subtitles")?.mapNotNull { (it as? JsonObject)?.toSubtitleTrack() }.orEmpty()

/** Parses one episode row of `GET /episodes`. */
internal fun JsonObject.toEpisodeSubtitles(): BazarrEpisodeSubtitles? {
    val id = optIntOf("episodeId", "episode_id", "id") ?: return null
    return BazarrEpisodeSubtitles(
        episodeId = id,
        seriesId = optIntOf("seriesId", "sonarrSeriesId") ?: 0,
        seasonNumber = optIntOf("seasonNumber", "season") ?: 0,
        episodeNumber = optIntOf("episodeNumber", "episode") ?: 0,
        title = optStringOf("title", "episodeTitle"),
        audioLanguages = this["audio_language"].toLanguageList(),
        subtitles = toSubtitleTracks(),
        missingLanguages = firstOf("missing_language", "missing_subtitles").toLanguageList(),
    )
}

/** Parses one movie row of `GET /movies`. */
internal fun JsonObject.toMovieSubtitles(): BazarrMovieSubtitles? {
    val id = optIntOf("radarrId", "radarr_id", "id") ?: return null
    return BazarrMovieSubtitles(
        radarrId = id,
        title = optStringOf("title"),
        year = optIntOf("year"),
        audioLanguages = this["audio_language"].toLanguageList(),
        subtitles = toSubtitleTracks(),
        missingLanguages = firstOf("missing_language", "missing_subtitles").toLanguageList(),
    )
}

internal fun JsonObject.toWantedEpisode(): BazarrWantedEpisode? {
    val id = optIntOf("episodeId", "episode_id", "id") ?: return null
    return BazarrWantedEpisode(
        episodeId = id,
        seriesId = optIntOf("seriesId", "sonarrSeriesId") ?: 0,
        seriesTitle = optStringOf("seriesTitle", "series_title"),
        title = optStringOf("title", "episodeTitle"),
        seasonNumber = optIntOf("seasonNumber", "season") ?: 0,
        episodeNumber = optIntOf("episodeNumber", "episode") ?: 0,
        missingLanguages = firstOf("missing_language", "missing_subtitles").toLanguageList(),
    )
}

internal fun JsonObject.toWantedMovie(): BazarrWantedMovie? {
    val id = optIntOf("radarrId", "radarr_id", "id") ?: return null
    return BazarrWantedMovie(
        radarrId = id,
        title = optStringOf("title"),
        year = optIntOf("year"),
        missingLanguages = firstOf("missing_language", "missing_subtitles").toLanguageList(),
    )
}

/**
 * Parses one history row. Timestamps arrive either as epoch seconds or as
 * ISO-ish local strings with a space separator; both are tolerated and a
 * missing timestamp stays 0 so sorting never crashes.
 */
internal fun JsonObject.toHistoryEvent(isMovie: Boolean): BazarrHistoryEvent? {
    val episodeId = optIntOf("episodeId", "episode_id")
    val radarrId = optIntOf("radarrId", "radarr_id")
    if (!isMovie && episodeId == null) return null
    if (isMovie && radarrId == null) return null
    val languageRef = (this["language"] as? JsonObject)?.toLanguageRef()
    val languageCode = when (val raw = this["language"]) {
        is JsonPrimitive -> raw.content
        else -> languageRef?.code2.orEmpty()
    }
    return BazarrHistoryEvent(
        action = optStringOf("action"),
        description = optStringOf("description"),
        title = optStringOf("title"),
        seriesTitle = optStringOf("seriesTitle", "series_title", "movieTitle"),
        languageCode2 = languageCode.ifBlank { languageRef?.code2.orEmpty() },
        provider = optStringOf("provider"),
        score = optDoubleOf("score"),
        timestampMillis = parseTimestampMillis(),
        seasonNumber = optIntOf("seasonNumber", "season"),
        episodeNumber = optIntOf("episodeNumber", "episode"),
        episodeId = episodeId,
        radarrId = radarrId,
        isMovie = isMovie,
    )
}

internal fun JsonObject.parseTimestampMillis(): Long {
    optDoubleOf("timestamp", "parsed_timestamp", "time")?.let { seconds ->
        // Bazarr sends epoch seconds; millis inputs are recognized by magnitude.
        return if (seconds > 100_000_000_000.0) seconds.toLong() else (seconds * 1000).toLong()
    }
    val text = optStringOf("timestamp", "parsed_timestamp", "time", "date")
    if (text.isNotEmpty()) {
        parseIsoish(text)?.let { return it }
    }
    return 0
}

/** Parses "2026-01-31T09:12:00Z", "2026-01-31 09:12:00", and offsets. */
internal fun parseIsoish(text: String): Long? = runCatching {
    java.time.Instant.parse(text).toEpochMilli()
}.recoverCatching {
    java.time.LocalDateTime.parse(text.replace(' ', 'T'))
        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}.getOrNull()

/** Parses one manual-search provider hit. */
internal fun JsonObject.toProviderResult(): BazarrProviderResult {
    val languageRef = (this["language"] as? JsonObject)?.toLanguageRef()
    val languageCode = when (val raw = this["language"]) {
        is JsonPrimitive -> raw.content
        else -> languageRef?.code2.orEmpty()
    }
    return BazarrProviderResult(
        provider = optStringOf("provider", "provider_name"),
        subtitleId = optStringOf("subtitle", "subtitle_id", "id"),
        languageCode2 = languageCode.ifBlank { languageRef?.code2.orEmpty() },
        languageName = languageRef?.name.orEmpty(),
        hearingImpaired = optBooleanOf("hearing_impaired", "hi") ?: false,
        forced = optBooleanOf("forced") ?: false,
        score = optDoubleOf("score", "match_score"),
        originalFormat = optBooleanOf("original_format") ?: false,
        releaseInfo = optStringOf("release_info", "release"),
        upvotes = optIntOf("upvotes"),
    )
}

/** Parses the languages endpoint into chip options, deduplicated by code. */
internal fun JsonObject.toLanguageOptions(): List<BazarrLanguageOption> {
    val rows = optArrayOf("data") ?: values.filterIsInstance<JsonArray>().firstOrNull() ?: JsonArray(emptyList())
    return rows.objects().mapNotNull { row ->
        val code = row.optStringOf("code2", "code")
        if (code.isBlank()) null else BazarrLanguageOption(code, row.optStringOf("name", "title"))
    }.distinctBy { it.code2 }
}

/** Reads the subtitle file text from a contents payload of unknown shape. */
internal fun JsonObject.toSubtitleContents(): String {
    for (name in listOf("data", "contents", "content", "text", "subtitle")) {
        val value = this[name] as? JsonPrimitive ?: continue
        if (value.isString) return value.content
    }
    return ""
}
