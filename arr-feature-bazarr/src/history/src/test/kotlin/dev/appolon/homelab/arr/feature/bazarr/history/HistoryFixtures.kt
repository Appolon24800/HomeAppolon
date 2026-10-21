package dev.appolon.homelab.arr.feature.bazarr.history

import dev.appolon.homelab.arr.api.bazarr.FakeBazarrLibrary
import dev.appolon.homelab.arr.api.bazarr.FakeBazarrSystem
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeActionQueue
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeEntityStore
import dev.appolon.homelab.arr.feature.bazarr.history.data.BazarrHistoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Builds a history repository over the library and system fakes. */
internal fun newHistoryRepository(
    library: FakeBazarrLibrary,
    system: FakeBazarrSystem,
    store: FakeEntityStore = FakeEntityStore(),
    queue: FakeActionQueue = FakeActionQueue(),
    serverId: ServerId = ServerId("s1"),
    now: () -> Long = System::currentTimeMillis,
    io: CoroutineDispatcher,
    pageLength: Int = BazarrHistoryRepository.DEFAULT_PAGE_LENGTH,
): BazarrHistoryRepository = BazarrHistoryRepository(
    serverId = serverId,
    store = store,
    episodesHistoryApi = library,
    moviesHistoryApi = library,
    episodesBlacklistApi = library,
    moviesBlacklistApi = library,
    languagesApi = system,
    actionQueue = queue,
    now = now,
    ioDispatcher = io,
    pageLength = pageLength,
)

/** One history row; timestamp is epoch seconds like the server sends. */
internal fun historyEventRow(
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

/** One blacklist row of either feed. */
internal fun blacklistRow(
    provider: String,
    subsId: String,
    language: String = "en",
    path: String = "/tv/show.en.srt",
    title: String = "Pilot",
    seriesTitle: String = "Wand Name",
    season: Int? = 1,
    episode: Int? = 1,
    episodeId: Int? = null,
    radarrId: Int? = null,
    timestamp: Long = 1_700_000_000,
): JsonObject = buildJsonObject {
    put("provider", provider)
    put("subs_id", subsId)
    put("language", language)
    put("subtitles_path", path)
    put("title", title)
    put("seriesTitle", seriesTitle)
    season?.let { put("seasonNumber", it) }
    episode?.let { put("episodeNumber", it) }
    episodeId?.let { put("episodeId", it) }
    radarrId?.let { put("radarrId", it) }
    put("timestamp", timestamp)
}

/** List payload wrapper: {"data": [...]}. */
internal fun historyDataPayload(rows: List<JsonObject>): JsonObject = buildJsonObject {
    put("data", JsonArray(rows))
}

/** One languages endpoint row. */
internal fun languageOptionRow(code2: String, name: String): JsonObject = buildJsonObject {
    put("code2", code2)
    put("name", name)
}
