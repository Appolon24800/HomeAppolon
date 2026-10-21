package dev.appolon.homelab.arr.core

/** TMDB image width buckets, ordered smallest to largest. */
enum class TmdbImageSize(val segment: String) {
    W92("w92"),
    W154("w154"),
    W185("w185"),
    W342("w342"),
    W500("w500"),
    W780("w780"),
    W1280("w1280"),
    ORIGINAL("original"),
}

/**
 * Cover kinds reported by the `coverType` field of Radarr, Sonarr, and Lidarr
 * image objects. This is the union across the three apps; each app only serves
 * the subset its entities report.
 */
enum class CoverType(val wireName: String) {
    POSTER("poster"),
    FANART("fanart"),
    BANNER("banner"),
    HEADSHOT("headshot"),
    SCREENSHOT("screenshot"),
    CLEARART("clearart"),
    LOGO("logo"),
    DISC("disc"),
    COVER("cover"),
    BACKDROP("backdrop"),
}

/**
 * Image URL building for poster, backdrop, and fanart art.
 *
 * Internal cover paths are relative so the connection layer resolves them
 * against the server base URL with its path prefix preserved
 * (`http://host:30025/radarr/`), authenticated by the same X-Api-Key header
 * as every other API call. Sizing uses the Servarr resized-variant convention
 * (`poster-500.jpg`); servers 404 sizes they have not generated, so prefer
 * null (original file) or a size the entity's images list advertises.
 * Bazarr exposes no mediacover route; its posters come from the
 * Radarr/Sonarr remote URLs.
 */
object ArrImages {

    private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/"

    /**
     * Builds a TMDB URL from the bare path the *arr APIs report for remote
     * posters and fanart. Absolute http(s) input (some `remotePoster` values
     * point at other CDNs) is returned unchanged; blank input returns null so
     * screens can fall back to a placeholder or the internal cover path.
     */
    fun tmdb(path: String?, size: TmdbImageSize = TmdbImageSize.W500): String? {
        if (path.isNullOrBlank()) return null
        val trimmed = path.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return trimmed
        }
        return TMDB_IMAGE_BASE + size.segment + "/" + trimmed.trimStart('/')
    }

    /**
     * Swaps the size segment of an existing image.tmdb.org URL, since the API
     * hands out fixed-size URLs. Non-TMDB URLs return unchanged.
     */
    fun tmdbSize(url: String, size: TmdbImageSize): String {
        if (!url.startsWith(TMDB_IMAGE_BASE)) return url
        val path = url.removePrefix(TMDB_IMAGE_BASE).substringAfter('/', "")
        if (path.isEmpty()) return url
        return TMDB_IMAGE_BASE + size.segment + "/" + path
    }

    /** Radarr movie cover: `/api/v3/mediacover/{movieId}/poster.jpg`. */
    fun radarrCover(movieId: Long, type: CoverType, sizePx: Int? = null): String =
        "/api/v3/mediacover/$movieId/${coverFile(type, sizePx)}"

    /** Sonarr series cover: `/api/v3/mediacover/{seriesId}/poster.jpg`. */
    fun sonarrCover(seriesId: Long, type: CoverType, sizePx: Int? = null): String =
        "/api/v3/mediacover/$seriesId/${coverFile(type, sizePx)}"

    /** Lidarr artist cover: `/api/v1/mediacover/artist/{artistId}/poster.jpg`. */
    fun lidarrArtistCover(artistId: Long, type: CoverType, sizePx: Int? = null): String =
        "/api/v1/mediacover/artist/$artistId/${coverFile(type, sizePx)}"

    /** Lidarr album cover: `/api/v1/mediacover/album/{albumId}/cover.jpg`. */
    fun lidarrAlbumCover(albumId: Long, type: CoverType, sizePx: Int? = null): String =
        "/api/v1/mediacover/album/$albumId/${coverFile(type, sizePx)}"

    private fun coverFile(type: CoverType, sizePx: Int?): String {
        require(sizePx == null || sizePx > 0) { "sizePx must be positive or null: $sizePx" }
        val suffix = if (sizePx == null) "" else "-$sizePx"
        return type.wireName + suffix + ".jpg"
    }
}
