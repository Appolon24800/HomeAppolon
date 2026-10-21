package dev.appolon.homelab.arr.api.radarr.api

import dev.appolon.homelab.arr.api.radarr.model.MediaCoverTypes
import kotlin.math.abs

/**
 * URL helpers for the Radarr `MediaCover` tag
 * (`GET /api/v3/mediacover/{movieId}/{filename}`), which serves raw image
 * bytes rather than JSON, so it has no Retrofit interface.
 *
 * Returned paths are relative without a leading slash so they resolve inside
 * a path-prefixed base URL (`http://host:30025/radarr/`). The image request
 * needs the same `X-Api-Key` header as every API call, so load them through
 * an OkHttp client built by the connection layer, not a plain Coil disk
 * fetch.
 */
object RadarrMediaCovers {

    /** Spec filename pattern: any name with a jpg, png, or gif extension. */
    private val FILENAME_PATTERN = Regex(""".+\.(jpg|png|gif)""")

    private const val EXTENSION = "jpg"
    private const val PATH = "api/v3/mediacover"

    /** Wire names of [MediaCoverTypes], spelled out so path building needs no reflection. */
    private val FILE_NAMES: Map<MediaCoverTypes, String> = mapOf(
        MediaCoverTypes.UNKNOWN to "unknown",
        MediaCoverTypes.POSTER to "poster",
        MediaCoverTypes.BANNER to "banner",
        MediaCoverTypes.FANART to "fanart",
        MediaCoverTypes.SCREENSHOT to "screenshot",
        MediaCoverTypes.HEADSHOT to "headshot",
        MediaCoverTypes.CLEARLOGO to "clearlogo",
    )

    /** Builds the cover path for an exact filename served by the movie's images list. */
    fun path(movieId: Int, filename: String): String {
        require(FILENAME_PATTERN.matches(filename)) {
            "filename must match (.+)\\.(jpg|png|gif): $filename"
        }
        return "$PATH/$movieId/$filename"
    }

    /**
     * Builds the path for a cover kind, optionally requesting a resized
     * variant. Radarr generates resized variants named `{type}-{size}.jpg`
     * and answers 404 for sizes it has not generated; pass null for the
     * original file.
     */
    fun path(movieId: Int, type: MediaCoverTypes, sizePx: Int? = null): String {
        require(sizePx == null || sizePx > 0) { "sizePx must be positive or null: $sizePx" }
        val suffix = if (sizePx == null) "" else "-${abs(sizePx)}"
        return path(movieId, "${FILE_NAMES.getValue(type)}$suffix.$EXTENSION")
    }
}
