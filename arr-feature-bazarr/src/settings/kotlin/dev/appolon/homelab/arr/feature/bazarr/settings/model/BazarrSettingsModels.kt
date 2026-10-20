package dev.appolon.homelab.arr.feature.bazarr.settings.model

import dev.appolon.homelab.arr.feature.bazarr.episodes.model.optBooleanOf
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.optDoubleOf
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.optIntOf
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.optStringOf
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.parseIsoish
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.parseTimestampMillis
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.rowsOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Typed view models for the Bazarr settings screens. The bundled Bazarr spec
 * declares no response schemas, so every endpoint returns a raw [JsonObject];
 * these models are the defensive projection of those payloads. Every field
 * defaults so a missing or renamed key degrades to a blank label instead of a
 * crash, and every model is [Serializable] so rows persist through the offline
 * [dev.appolon.homelab.arr.data.EntityStore].
 */

/** One language of the server's enabled-language settings list. */
@Serializable
data class BazarrLanguageSetting(
    val code2: String,
    val name: String = "",
    val enabled: Boolean = false,
    val isDefault: Boolean = false,
    val hearingImpaired: Boolean = false,
    val forced: Boolean = false,
) {
    val label: String get() = if (name.isBlank()) code2 else "$name ($code2)"
}

/** One language slot inside a language profile, with its HI and forced flags. */
@Serializable
data class BazarrProfileLanguage(
    val code2: String = "",
    val hearingImpaired: Boolean = false,
    val forced: Boolean = false,
)

/** One language profile: cutoff language, ordered slots, and release rules. */
@Serializable
data class BazarrLanguageProfile(
    val profileId: Int,
    val name: String = "",
    val cutoff: String = "",
    val items: List<BazarrProfileLanguage> = emptyList(),
    val mustContain: List<String> = emptyList(),
    val mustNotContain: List<String> = emptyList(),
    val originalFormat: List<String> = emptyList(),
) {
    val label: String get() = name.ifBlank { "Profile $profileId" }
}

/** Health row of one subtitle provider as the providers endpoint reports it. */
@Serializable
data class BazarrProviderStatus(
    val provider: String,
    val status: String = "",
    val retry: String = "",
    /** Dynamic fields beyond provider, status, and retry that the server adds. */
    val extraFields: List<Pair<String, String>> = emptyList(),
)

/** One scheduler task: its interval, next run, and whether it is running. */
@Serializable
data class BazarrScheduledTask(
    val jobId: String,
    val name: String = "",
    val intervalLabel: String = "",
    val nextRunMillis: Long = 0,
    val running: Boolean = false,
) {
    val label: String get() = name.ifBlank { jobId }
}

/** One row of the job queue with its remaining progress and status. */
@Serializable
data class BazarrJobRow(
    val id: Int,
    val queueName: String = "",
    val jobName: String = "",
    val title: String = "",
    val progressRemaining: String = "",
    val status: String = "",
)

/** One flattened key/value row of the system status payload. */
@Serializable
data class BazarrStatusRow(val key: String, val value: String)

/** One backup file with its size and creation date as the server formats them. */
@Serializable
data class BazarrBackupFile(
    val filename: String,
    val type: String = "",
    val size: String = "",
    val date: String = "",
)

/** One log entry; timestampMillis exists so the viewer can sort newest first. */
@Serializable
data class BazarrLogEntry(
    val message: String = "",
    val name: String = "",
    val type: String = "",
    val timestampMillis: Long = 0,
)

/*
 * Defensive JsonElement parsers. Each tolerates the candidate key spellings
 * Bazarr has used across versions, wrong primitive kinds, and absent keys.
 */

/** Parses one language row; rows without a code2 are dropped. */
internal fun JsonObject.toLanguageSetting(): BazarrLanguageSetting? {
    val code = optStringOf("code2", "code", "language")
    if (code.isBlank()) return null
    return BazarrLanguageSetting(
        code2 = code,
        name = optStringOf("name", "title"),
        enabled = optBooleanOf("enabled", "is_enabled") ?: false,
        isDefault = optBooleanOf("default", "is_default") ?: false,
        hearingImpaired = optBooleanOf("hi", "hearing_impaired") ?: false,
        forced = optBooleanOf("forced") ?: false,
    )
}

internal fun JsonObject.toLanguageSettings(): List<BazarrLanguageSetting> =
    rowsOf().mapNotNull { it.toLanguageSetting() }

/** Parses one profile slot; the language member is a code string or an object. */
internal fun JsonObject.toProfileLanguage(): BazarrProfileLanguage? = when (
    val language = this["language"]
) {
    is JsonObject -> BazarrProfileLanguage(
        code2 = language.optStringOf("code2", "code", "language"),
        hearingImpaired = optBooleanOf("hi", "hearing_impaired") ?: false,
        forced = optBooleanOf("forced") ?: false,
    )

    is JsonPrimitive -> BazarrProfileLanguage(
        code2 = language.content,
        hearingImpaired = optBooleanOf("hi", "hearing_impaired") ?: false,
        forced = optBooleanOf("forced") ?: false,
    )

    else -> null
}

/** Rule lists carry bare strings; some builds wrap each rule in an object. */
private fun ruleListOf(element: JsonElement?): List<String> =
    (element as? JsonArray)?.mapNotNull { row ->
        when (row) {
            is JsonPrimitive -> row.content.ifBlank { null }
            is JsonObject -> row.optStringOf("value", "pattern", "text").ifBlank { null }
            else -> null
        }
    }.orEmpty()

internal fun JsonObject.toLanguageProfile(): BazarrLanguageProfile? {
    val id = optIntOf("profileId", "profile_id", "id") ?: return null
    val cutoff = when (val raw = this["cutoff"]) {
        is JsonPrimitive -> raw.content
        is JsonObject -> raw.optStringOf("code2", "code")
        else -> ""
    }
    return BazarrLanguageProfile(
        profileId = id,
        name = optStringOf("name", "title"),
        cutoff = cutoff,
        items = rowsOfItems(),
        mustContain = ruleListOf(this["mustContain"]),
        mustNotContain = ruleListOf(this["mustNotContain"]),
        originalFormat = ruleListOf(this["originalFormat"]),
    )
}

/** Items arrive as an array of slot objects, distinct from the rowsOf wrapper. */
private fun JsonObject.rowsOfItems(): List<BazarrProfileLanguage> =
    (this["items"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.toProfileLanguage() }.orEmpty()

internal fun JsonObject.toLanguageProfiles(): List<BazarrLanguageProfile> =
    rowsOf().mapNotNull { it.toLanguageProfile() }

/** Parses one provider row, keeping any dynamic primitive fields it carries. */
internal fun JsonObject.toProviderStatus(): BazarrProviderStatus? {
    val name = optStringOf("provider", "provider_name", "name")
    if (name.isBlank()) return null
    val reserved = setOf("provider", "provider_name", "name", "status", "retry", "retry_in")
    val extra = entries.mapNotNull { (key, value) ->
        if (key in reserved || value !is JsonPrimitive || !value.isString) {
            null
        } else {
            key to value.content
        }
    }
    return BazarrProviderStatus(
        provider = name,
        status = optStringOf("status"),
        retry = optStringOf("retry", "retry_in"),
        extraFields = extra,
    )
}

internal fun JsonObject.toProviderStatuses(): List<BazarrProviderStatus> =
    rowsOf().mapNotNull { it.toProviderStatus() }

internal fun JsonObject.toScheduledTask(): BazarrScheduledTask? {
    val id = optStringOf("job_id", "jobId", "jobID", "id")
    if (id.isBlank()) return null
    return BazarrScheduledTask(
        jobId = id,
        name = optStringOf("name", "jobName"),
        intervalLabel = optStringOf("interval"),
        nextRunMillis = parseNextRunMillis(),
        running = optBooleanOf("running", "active") ?: false,
    )
}

/** Next-run times arrive as epoch seconds or ISO-ish strings; both parse. */
internal fun JsonObject.parseNextRunMillis(): Long {
    optDoubleOf("next_run_time", "nextExecutionTime", "next_run")?.let { seconds ->
        // Bazarr sends epoch seconds; millis inputs are recognized by magnitude.
        return if (seconds > 100_000_000_000.0) seconds.toLong() else (seconds * 1000).toLong()
    }
    val text = optStringOf("next_run_time", "nextExecutionTime", "next_run")
    if (text.isNotEmpty()) parseIsoish(text)?.let { return it }
    return 0
}

internal fun JsonObject.toScheduledTasks(): List<BazarrScheduledTask> =
    rowsOf().mapNotNull { it.toScheduledTask() }

internal fun JsonObject.toJobRow(): BazarrJobRow? {
    val id = optIntOf("id", "job_id") ?: return null
    return BazarrJobRow(
        id = id,
        queueName = optStringOf("queueName", "queue_name", "queue"),
        jobName = optStringOf("jobName", "job_name"),
        title = optStringOf("title", "seriesTitle", "movieTitle"),
        progressRemaining = optStringOf("progress_remaining", "progress"),
        status = optStringOf("status"),
    )
}

internal fun JsonObject.toJobRows(): List<BazarrJobRow> = rowsOf().mapNotNull { it.toJobRow() }

/**
 * Flattens the status payload into label rows. Bazarr returns the object
 * directly or wrapped in a data member; nested objects and arrays render as
 * their compact JSON so no reported field is silently dropped.
 */
internal fun JsonObject.toStatusRows(): List<BazarrStatusRow> {
    val source = (this["data"] as? JsonObject) ?: this
    return source.entries.mapNotNull { (key, value) ->
        val text = when (value) {
            is JsonPrimitive -> value.content
            is JsonObject, is JsonArray -> value.toString()
        }
        if (text.isBlank()) null else BazarrStatusRow(key, text)
    }
}

/** Markers of keys the General page surfaces ahead of the raw field dump. */
private val GENERAL_KEY_MARKERS = listOf(
    "sonarr", "radarr", "folder", "captcha", "proxy", "provider",
    "page_size", "single_language", "base_url", "language", "theme",
)

/** Status rows whose keys look like general settings (folder use, captcha...). */
internal fun List<BazarrStatusRow>.generalStatusRows(): List<BazarrStatusRow> = filter { row ->
    val key = row.key.lowercase()
    GENERAL_KEY_MARKERS.any { marker -> key.contains(marker) }
}

/** Every status row that is not a general-interest key. */
internal fun List<BazarrStatusRow>.otherStatusRows(): List<BazarrStatusRow> =
    filterNot { row ->
        val key = row.key.lowercase()
        GENERAL_KEY_MARKERS.any { marker -> key.contains(marker) }
    }

internal fun JsonObject.toBackupFile(): BazarrBackupFile? {
    val filename = optStringOf("filename", "name", "file")
    if (filename.isBlank()) return null
    return BazarrBackupFile(
        filename = filename,
        type = optStringOf("type"),
        size = optStringOf("size"),
        date = optStringOf("date", "created"),
    )
}

internal fun JsonObject.toBackupFiles(): List<BazarrBackupFile> =
    rowsOf().mapNotNull { it.toBackupFile() }

internal fun JsonObject.toLogEntry(): BazarrLogEntry = BazarrLogEntry(
    message = optStringOf("message", "msg"),
    name = optStringOf("name"),
    type = optStringOf("type", "level"),
    timestampMillis = parseTimestampMillis(),
)

/** Log rows without a message say nothing; they are dropped before caching. */
internal fun JsonObject.toLogEntries(): List<BazarrLogEntry> =
    rowsOf().map { it.toLogEntry() }.filter { it.message.isNotBlank() }
