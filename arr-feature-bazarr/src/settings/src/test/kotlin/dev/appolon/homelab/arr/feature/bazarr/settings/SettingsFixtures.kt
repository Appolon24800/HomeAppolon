package dev.appolon.homelab.arr.feature.bazarr.settings

import dev.appolon.homelab.arr.api.bazarr.FakeBazarrSubtitles
import dev.appolon.homelab.arr.api.bazarr.FakeBazarrSystem
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeActionQueue
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeEntityStore
import dev.appolon.homelab.arr.feature.bazarr.settings.data.BazarrSettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Builds a settings repository over the system and subtitles fakes. */
internal fun newSettingsRepository(
    system: FakeBazarrSystem,
    subtitles: FakeBazarrSubtitles,
    store: FakeEntityStore = FakeEntityStore(),
    queue: FakeActionQueue = FakeActionQueue(),
    serverId: ServerId = ServerId("s1"),
    now: () -> Long = System::currentTimeMillis,
    io: CoroutineDispatcher,
): BazarrSettingsRepository = BazarrSettingsRepository(
    serverId = serverId,
    store = store,
    languagesApi = system,
    languageProfilesApi = system,
    providersApi = subtitles,
    webhookSettingsApi = system,
    tasksApi = system,
    jobsApi = system,
    statusApi = system,
    backupsApi = system,
    logsApi = system,
    actionQueue = queue,
    now = now,
    ioDispatcher = io,
)

/** One languages endpoint row. */
internal fun languageRow(
    code2: String,
    name: String,
    enabled: Boolean = false,
    hi: Boolean = false,
    forced: Boolean = false,
): JsonObject = buildJsonObject {
    put("code2", code2)
    put("name", name)
    put("enabled", enabled)
    put("hi", hi)
    put("forced", forced)
}

/** One profile slot: the code with its hearing impaired and forced flags. */
internal fun profileItem(code2: String, hi: Boolean = false, forced: Boolean = false): JsonObject =
    buildJsonObject {
        put("language", code2)
        put("hi", hi)
        put("forced", forced)
    }

/** One language profiles row with cutoff and release rules. */
internal fun profileRow(
    profileId: Int,
    name: String,
    cutoff: String = "",
    items: List<JsonObject> = emptyList(),
    mustContain: List<String> = emptyList(),
    mustNotContain: List<String> = emptyList(),
    originalFormat: List<String> = emptyList(),
): JsonObject = buildJsonObject {
    put("profileId", profileId)
    put("name", name)
    if (cutoff.isNotEmpty()) put("cutoff", cutoff)
    if (items.isNotEmpty()) put("items", JsonArray(items))
    if (mustContain.isNotEmpty()) {
        put("mustContain", JsonArray(mustContain.map { JsonPrimitive(it) }))
    }
    if (mustNotContain.isNotEmpty()) {
        put("mustNotContain", JsonArray(mustNotContain.map { JsonPrimitive(it) }))
    }
    if (originalFormat.isNotEmpty()) {
        put("originalFormat", JsonArray(originalFormat.map { JsonPrimitive(it) }))
    }
}

/** One providers health row with optional dynamic fields. */
internal fun providerRow(
    provider: String,
    status: String = "Good",
    retry: String = "0",
    extra: Map<String, String> = emptyMap(),
): JsonObject = buildJsonObject {
    put("provider", provider)
    put("status", status)
    put("retry", retry)
    extra.forEach { (key, value) -> put(key, value) }
}

/** One scheduler task row; nextRun defaults to a fixed ISO instant. */
internal fun taskRow(
    jobId: String,
    name: String,
    interval: String = "1h",
    nextRun: String = "2026-01-31T09:12:00Z",
    running: Boolean = false,
): JsonObject = buildJsonObject {
    put("job_id", jobId)
    put("name", name)
    put("interval", interval)
    put("next_run_time", nextRun)
    put("running", running)
}

/** One job queue row. */
internal fun jobRow(
    id: Int,
    queueName: String = "search_missing_subtitle",
    title: String = "Some Series",
    status: String = "pending",
): JsonObject = buildJsonObject {
    put("id", id)
    put("queueName", queueName)
    put("jobName", "Search missing subtitles")
    put("title", title)
    put("progress_remaining", "50.0%")
    put("status", status)
}

/** One backup file row. */
internal fun backupRow(
    filename: String,
    type: String = "backup",
    size: String = "1.2 MB",
    date: String = "2026-01-30 10:00:00",
): JsonObject = buildJsonObject {
    put("type", type)
    put("filename", filename)
    put("size", size)
    put("date", date)
}

/** One log entry row; timestamp is epoch seconds like the server sends. */
internal fun logRow(
    message: String,
    type: String = "INFO",
    timestamp: Long = 1_760_000_000,
    name: String = "bazarr",
): JsonObject = buildJsonObject {
    put("message", message)
    put("type", type)
    put("timestamp", timestamp)
    put("name", name)
}

/** List payload wrapper: {"data": [...]}. */
internal fun settingsDataPayload(rows: List<JsonObject>): JsonObject = buildJsonObject {
    put("data", JsonArray(rows))
}
