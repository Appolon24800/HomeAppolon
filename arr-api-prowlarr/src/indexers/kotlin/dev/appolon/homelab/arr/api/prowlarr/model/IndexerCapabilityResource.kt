package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SearchParam {
    @SerialName("q") Q,
}

@Serializable
enum class TvSearchParam {
    @SerialName("q") Q,
    @SerialName("season") SEASON,
    @SerialName("ep") EP,
    @SerialName("imdbId") IMDB_ID,
    @SerialName("tvdbId") TVDB_ID,
    @SerialName("rId") R_ID,
    @SerialName("tvMazeId") TV_MAZE_ID,
    @SerialName("traktId") TRAKT_ID,
    @SerialName("tmdbId") TMDB_ID,
    @SerialName("doubanId") DOUBAN_ID,
    @SerialName("genre") GENRE,
    @SerialName("year") YEAR,
}

@Serializable
enum class MovieSearchParam {
    @SerialName("q") Q,
    @SerialName("imdbId") IMDB_ID,
    @SerialName("tmdbId") TMDB_ID,
    @SerialName("imdbTitle") IMDB_TITLE,
    @SerialName("imdbYear") IMDB_YEAR,
    @SerialName("traktId") TRAKT_ID,
    @SerialName("genre") GENRE,
    @SerialName("doubanId") DOUBAN_ID,
    @SerialName("year") YEAR,
}

@Serializable
enum class MusicSearchParam {
    @SerialName("q") Q,
    @SerialName("album") ALBUM,
    @SerialName("artist") ARTIST,
    @SerialName("label") LABEL,
    @SerialName("year") YEAR,
    @SerialName("genre") GENRE,
    @SerialName("track") TRACK,
}

@Serializable
enum class BookSearchParam {
    @SerialName("q") Q,
    @SerialName("title") TITLE,
    @SerialName("author") AUTHOR,
    @SerialName("publisher") PUBLISHER,
    @SerialName("genre") GENRE,
    @SerialName("year") YEAR,
}

/**
 * What one indexer can search and which Newznab categories it covers. The spec
 * enumerates every search parameter, so they are enums rather than strings.
 */
@Serializable
data class IndexerCapabilityResource(
    val id: Int = 0,
    val limitsMax: Int? = null,
    val limitsDefault: Int? = null,
    val categories: List<IndexerCategory>? = null,
    val supportsRawSearch: Boolean = false,
    val searchParams: List<SearchParam>? = null,
    val tvSearchParams: List<TvSearchParam>? = null,
    val movieSearchParams: List<MovieSearchParam>? = null,
    val musicSearchParams: List<MusicSearchParam>? = null,
    val bookSearchParams: List<BookSearchParam>? = null,
)
