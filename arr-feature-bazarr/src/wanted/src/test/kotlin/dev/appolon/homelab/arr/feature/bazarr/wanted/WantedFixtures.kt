package dev.appolon.homelab.arr.feature.bazarr.wanted

import dev.appolon.homelab.arr.api.bazarr.FakeBazarrLibrary
import dev.appolon.homelab.arr.api.bazarr.FakeBazarrSystem
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeActionQueue
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeEntityStore
import dev.appolon.homelab.arr.feature.bazarr.wanted.data.BazarrWantedRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Builds a wanted repository over the library and system fakes. */
internal fun newWantedRepository(
    library: FakeBazarrLibrary,
    system: FakeBazarrSystem,
    store: FakeEntityStore = FakeEntityStore(),
    queue: FakeActionQueue = FakeActionQueue(),
    serverId: ServerId = ServerId("s1"),
    now: () -> Long = System::currentTimeMillis,
    io: CoroutineDispatcher,
): BazarrWantedRepository = BazarrWantedRepository(
    serverId = serverId,
    store = store,
    episodesWantedApi = library,
    moviesWantedApi = library,
    episodesSubtitlesApi = library,
    moviesSubtitlesApi = library,
    badgesApi = library,
    languagesApi = system,
    actionQueue = queue,
    now = now,
    ioDispatcher = io,
)

/** List payload wrapper: {"data": [...], "total": n}. */
internal fun dataPayload(rows: List<JsonObject>): JsonObject = buildJsonObject {
    put("data", JsonArray(rows))
    put("total", rows.size)
}

/** One wanted episode row of a GET episodes/wanted payload. */
internal fun wantedEpisodeRow(
    episodeId: Int,
    seriesId: Int,
    seriesTitle: String = "Wand Name",
    season: Int = 1,
    episode: Int = 1,
    title: String = "Pilot",
    missing: List<JsonObject> = emptyList(),
): JsonObject = buildJsonObject {
    put("episodeId", episodeId)
    put("seriesId", seriesId)
    put("seriesTitle", seriesTitle)
    put("seasonNumber", season)
    put("episodeNumber", episode)
    put("title", title)
    if (missing.isNotEmpty()) put("missing_language", JsonArray(missing))
}

/** One wanted movie row of a GET movies/wanted payload. */
internal fun wantedMovieRow(
    radarrId: Int,
    title: String = "Arrival",
    year: Int? = null,
    missing: List<JsonObject> = emptyList(),
): JsonObject = buildJsonObject {
    put("radarrId", radarrId)
    put("title", title)
    year?.let { put("year", it) }
    if (missing.isNotEmpty()) put("missing_language", JsonArray(missing))
}

/** One missing-language reference. */
internal fun missingLanguageRow(code2: String, name: String = "", hi: Boolean = false): JsonObject =
    buildJsonObject {
        put("code2", code2)
        if (name.isNotBlank()) put("name", name)
        if (hi) put("hi", true)
    }
