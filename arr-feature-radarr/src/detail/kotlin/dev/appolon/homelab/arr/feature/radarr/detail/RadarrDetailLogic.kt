package dev.appolon.homelab.arr.feature.radarr.detail

import dev.appolon.homelab.arr.api.radarr.model.CreditResource
import dev.appolon.homelab.arr.api.radarr.model.CreditType
import dev.appolon.homelab.arr.api.radarr.model.MediaCoverTypes
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.core.ArrImages
import dev.appolon.homelab.arr.data.QueuedAction
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Pure decision logic for the Radarr movie detail screens: image URL
 * resolution, credit ordering, cache freshness, queued-action attribution,
 * edit patches, and command body building. Everything here is deterministic
 * and unit-tested without Android.
 */
object RadarrDetailLogic {

    /**
     * Resolves a server-relative cover path against a base URL that may carry
     * a path prefix (`http://host:30025/radarr/`). Absolute URLs pass through
     * unchanged; blank input yields null so callers fall back to placeholders.
     */
    fun serverPath(baseUrl: String, path: String?): String? {
        val trimmed = path?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return trimmed
        }
        return baseUrl.trimEnd('/') + "/" + trimmed.trimStart('/')
    }

    /**
     * Poster URL: the server-served cover first (best quality, authenticated
     * by the image loader), then the TMDB remote cover, then the movie's
     * `remotePoster` field.
     */
    fun posterUrl(baseUrl: String, movie: MovieResource): String? {
        cover(movie, MediaCoverTypes.POSTER)?.let { cover ->
            serverPath(baseUrl, cover.url)?.let { return it }
            cover.remoteUrl?.takeIf { it.isNotBlank() }?.let { remote ->
                return ArrImages.tmdb(remote)
            }
        }
        return ArrImages.tmdb(movie.remotePoster)
    }

    /** Backdrop (fanart) URL used for the detail hero. */
    fun backdropUrl(baseUrl: String, movie: MovieResource): String? {
        cover(movie, MediaCoverTypes.FANART)?.let { fanart ->
            serverPath(baseUrl, fanart.url)?.let { return it }
            fanart.remoteUrl?.takeIf { it.isNotBlank() }?.let { remote ->
                return ArrImages.tmdb(remote)
            }
        }
        return null
    }

    /** Headshot URL for a credit, preferring the server-served image. */
    fun headshotUrl(baseUrl: String, credit: CreditResource): String? {
        val headshot = credit.images.orEmpty()
            .firstOrNull { it.coverType == MediaCoverTypes.HEADSHOT }
            ?: return null
        return serverPath(baseUrl, headshot.url)
            ?: headshot.remoteUrl?.takeIf { it.isNotBlank() }?.let(ArrImages::tmdb)
    }

    private fun cover(movie: MovieResource, type: MediaCoverTypes) =
        movie.images.orEmpty().firstOrNull { it.coverType == type }

    /** Cast ordered as credited (the `order` field), missing orders last. */
    fun castOf(credits: List<CreditResource>): List<CreditResource> =
        credits.filter { it.type != CreditType.CREW }.sortedWith(
            compareBy({ it.order <= 0 }, { it.order }, { it.personName.orEmpty() }),
        )

    /** Crew ordered by job then name; displayed under the cast row. */
    fun crewOf(credits: List<CreditResource>): List<CreditResource> =
        credits.filter { it.type == CreditType.CREW }.sortedWith(
            compareBy({ it.job.orEmpty() }, { it.personName.orEmpty() }),
        )

    /** True when a cached payload fetched at [fetchedAtMillis] is still usable. */
    fun isFresh(fetchedAtMillis: Long?, nowMillis: Long, ttlMillis: Long): Boolean {
        if (fetchedAtMillis == null || ttlMillis <= 0) return false
        return nowMillis - fetchedAtMillis in 0 until ttlMillis
    }

    /**
     * Queued actions that target one movie, regardless of which screen
     * enqueued them, so the detail page can badge pending offline work.
     * Matches `/movie/{id}` path segments and command bodies carrying the id.
     */
    fun actionsForMovie(actions: List<QueuedAction>, movieId: Int): List<QueuedAction> {
        val pathNeedle = "/movie/$movieId"
        return actions.filter { action ->
            val body = action.bodyJson
            when {
                action.path.contains(pathNeedle) -> true
                action.path == "api/v3/command" && body != null ->
                    body.contains("\"movieId\":$movieId") ||
                        body.contains("\"movieIds\":[$movieId]")
                else -> false
            }
        }
    }

    /** Applies edit-sheet values onto a stored movie resource for a PUT. */
    fun applyEdits(movie: MovieResource, edits: EditableMovieUi): MovieResource = movie.copy(
        monitored = edits.monitored,
        qualityProfileId = edits.qualityProfileId,
        minimumAvailability = edits.minimumAvailability ?: movie.minimumAvailability,
        path = edits.path.ifBlank { movie.path },
        tags = edits.tags,
    )

    /** Body for `MoviesSearch`: the server searches indexers and grabs the best release. */
    fun searchCommandBody(movieId: Int): JsonObject = buildJsonObject {
        put("name", "MoviesSearch")
        put("movieIds", kotlinx.serialization.json.buildJsonArray { add(JsonPrimitive(movieId)) })
    }

    /** Body for `RenameFiles`: Radarr renames the movie's files to the current naming spec. */
    fun renameCommandBody(movieId: Int): JsonObject = buildJsonObject {
        put("name", "RenameFiles")
        put("movieId", movieId)
    }

    /** Body for `DownloadedMoviesScan`: imports whatever is inside [folder]. */
    fun importScanCommandBody(folder: String): JsonObject = buildJsonObject {
        put("name", "DownloadedMoviesScan")
        put("path", folder)
    }
}
