package dev.appolon.homelab.arr.feature.bazarr.episodes.data

import dev.appolon.homelab.arr.api.bazarr.FakeBazarrLibrary
import dev.appolon.homelab.arr.api.bazarr.FakeBazarrSubtitles
import dev.appolon.homelab.arr.api.bazarr.FakeBazarrSystem
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeActionQueue
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeEntityStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Builds a repository over the three api fakes plus the in-memory store. */
internal fun newRepository(
    library: FakeBazarrLibrary,
    subtitles: FakeBazarrSubtitles,
    system: FakeBazarrSystem,
    store: FakeEntityStore = FakeEntityStore(),
    queue: FakeActionQueue = FakeActionQueue(),
    serverId: ServerId = ServerId("s1"),
    now: () -> Long = System::currentTimeMillis,
    io: CoroutineDispatcher,
): BazarrSubtitlesRepository = BazarrSubtitlesRepository(
    serverId = serverId,
    store = store,
    seriesApi = library,
    episodesApi = library,
    episodesSubtitlesApi = library,
    episodesWantedApi = library,
    episodesHistoryApi = library,
    moviesApi = library,
    moviesSubtitlesApi = library,
    moviesWantedApi = library,
    moviesHistoryApi = library,
    providersEpisodesApi = subtitles,
    providersMoviesApi = subtitles,
    subtitleContentsApi = subtitles,
    languagesApi = system,
    actionQueue = queue,
    now = now,
    ioDispatcher = io,
)

/** One language reference row: {"code2": "en", "name": "English", "hi": false, "forced": false}. */
internal fun languageRow(code2: String, name: String = "", hi: Boolean = false, forced: Boolean = false): JsonObject =
    buildJsonObject {
        put("code2", code2)
        if (name.isNotBlank()) put("name", name)
        if (hi) put("hi", true)
        if (forced) put("forced", true)
    }

/** One attached subtitle row of an episode or movie payload. */
internal fun trackRow(
    language: String,
    path: String = "/tv/show.$language.srt",
    hi: Boolean = false,
    forced: Boolean = false,
    score: Double? = null,
    provider: String = "internal",
): JsonObject = buildJsonObject {
    put("path", path)
    put("language", language)
    if (hi) put("hearing_impaired", true)
    if (forced) put("forced", true)
    score?.let { put("score", it) }
    put("provider", provider)
}

/** One episode row of a GET /episodes payload. */
internal fun episodeRow(
    episodeId: Int,
    seriesId: Int = 12,
    season: Int = 1,
    episode: Int = 1,
    title: String = "Pilot",
    subtitles: List<JsonObject> = emptyList(),
    missing: List<JsonObject> = emptyList(),
): JsonObject = buildJsonObject {
    put("episodeId", episodeId)
    put("seriesId", seriesId)
    put("seasonNumber", season)
    put("episodeNumber", episode)
    put("title", title)
    if (subtitles.isNotEmpty()) put("subtitles", JsonArray(subtitles))
    if (missing.isNotEmpty()) put("missing_language", JsonArray(missing))
}

/** One movie row of a GET /movies payload. */
internal fun movieRow(
    radarrId: Int,
    title: String = "Movie",
    year: Int? = null,
    subtitles: List<JsonObject> = emptyList(),
    missing: List<JsonObject> = emptyList(),
): JsonObject = buildJsonObject {
    put("radarrId", radarrId)
    put("title", title)
    year?.let { put("year", it) }
    if (subtitles.isNotEmpty()) put("subtitles", JsonArray(subtitles))
    if (missing.isNotEmpty()) put("missing_language", JsonArray(missing))
}

/** One history row; timestamp is epoch seconds like the server sends. */
internal fun historyRow(
    action: String,
    timestamp: Long,
    title: String = "Pilot",
    language: String = "en",
    provider: String = "opensubtitles",
    score: Double? = null,
    episodeId: Int? = null,
    radarrId: Int? = null,
): JsonObject = buildJsonObject {
    put("action", action)
    put("timestamp", timestamp)
    put("title", title)
    put("language", language)
    put("provider", provider)
    score?.let { put("score", it) }
    episodeId?.let { put("episodeId", it) }
    radarrId?.let { put("radarrId", it) }
}

/** One manual search provider hit. */
internal fun providerRow(
    provider: String,
    subtitleId: String,
    language: String = "en",
    score: Double? = 100.0,
    hi: Boolean = false,
    releaseInfo: String = "show.1080p.group",
): JsonObject = buildJsonObject {
    put("provider", provider)
    put("subtitle", subtitleId)
    put("language", language)
    score?.let { put("score", it) }
    if (hi) put("hearing_impaired", true)
    put("release_info", releaseInfo)
}
