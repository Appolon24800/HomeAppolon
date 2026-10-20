package dev.appolon.homelab.arr.feature.radarr.addnew.data

import dev.appolon.homelab.arr.api.radarr.model.AddMovieMethod
import dev.appolon.homelab.arr.api.radarr.model.AddMovieOptions
import dev.appolon.homelab.arr.api.radarr.model.CollectionResource
import dev.appolon.homelab.arr.api.radarr.model.ImportListMoviesResource
import dev.appolon.homelab.arr.api.radarr.model.MediaCoverTypes
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.MonitorTypes
import dev.appolon.homelab.arr.api.radarr.model.MovieStatusType
import dev.appolon.homelab.arr.core.ArrImages

/**
 * Pure presentation and payload logic for the Add new screens, kept free of
 * Android and IO so every rule is unit testable.
 */
object AddNewLogic {

    /** Radarr's named command for searching a set of movies. */
    const val COMMAND_MOVIES_SEARCH = "MoviesSearch"

    private val YEARS = Regex("""(?:19|20)\d{2}""")

    /**
     * Splits a raw query into a search term and a disambiguating year. A year
     * counts only when it stands alone at the end, wrapped in parentheses or
     * brackets ("Dune (2021)", "Dune [2021]") or as the final word ("Dune
     * 2021"). Anything else ("Blade 2049 does not exist 2049", "1984")
     * stays part of the term so normal titles keep their shape.
     */
    fun parseSearchQuery(raw: String): ParsedSearchQuery {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ParsedSearchQuery.EMPTY

        listOf(
            Regex("""^(.*?)[\s]*[\[(]\s*((?:19|20)\d{2})\s*[\])]$"""),
            Regex("""^(.*?)[\s]+((?:19|20)\d{2})$"""),
        ).forEach { pattern ->
            val match = pattern.find(trimmed) ?: return@forEach
            val term = match.groupValues[1].trim()
            if (term.isNotEmpty()) {
                return ParsedSearchQuery(term = term, year = match.groupValues[2].toInt())
            }
        }
        return ParsedSearchQuery(term = trimmed, year = null)
    }

    /**
     * Orders lookup results so movies whose year equals the parsed year come
     * first; within each group the server order is preserved.
     */
    fun <T> rankByYear(items: List<T>, year: Int?, yearOf: (T) -> Int): List<T> {
        if (year == null) return items
        val matching = items.filter { yearOf(it) == year }
        val rest = items.filter { yearOf(it) != year }
        return matching + rest
    }

    /** Splits a discovery feed into its flag-driven sections, dropping empties. */
    fun discoverySections(items: List<ImportListMoviesResource>): DiscoverySections {
        val seen = HashSet<Int>()
        fun pick(flag: (ImportListMoviesResource) -> Boolean): List<ImportListMoviesResource> =
            items.filter(flag).filter { seen.add(it.tmdbId) }
        return DiscoverySections(
            recommended = pick { it.isRecommendation },
            trending = pick { it.isTrending },
            popular = pick { it.isPopular },
        )
    }

    /**
     * Poster URL for a lookup result: these carry no local id, so only TMDB
     * remote art applies; null means the caller shows the monogram tile.
     */
    fun lookupPosterUrl(movie: MovieResource): String? {
        val poster = movie.images.orEmpty().firstOrNull { it.coverType == MediaCoverTypes.POSTER }
        return ArrImages.tmdb(poster?.remoteUrl) ?: ArrImages.tmdb(movie.remotePoster)
    }

    /** Poster URL for a discovery item, TMDB remote art only like lookup. */
    fun discoveryPosterUrl(item: ImportListMoviesResource): String? {
        val poster = item.images.orEmpty().firstOrNull { it.coverType == MediaCoverTypes.POSTER }
        return ArrImages.tmdb(poster?.remoteUrl) ?: ArrImages.tmdb(item.remotePoster)
    }

    /** Poster URL for a collection row. */
    fun collectionPosterUrl(collection: CollectionResource): String? {
        val poster = collection.images.orEmpty()
            .firstOrNull { it.coverType == MediaCoverTypes.POSTER }
        return ArrImages.tmdb(poster?.remoteUrl)
    }

    /**
     * Converts a discovery item into the MovieResource the add sheet and the
     * POST /api/v3/movie body consume, carrying only the metadata Radarr
     * accepts for a new movie (no library-only fields such as statistics).
     */
    fun toCandidate(item: ImportListMoviesResource): MovieResource = MovieResource(
        title = item.title,
        sortTitle = item.sortTitle,
        originalLanguage = item.originalLanguage,
        status = item.status,
        overview = item.overview,
        inCinemas = item.inCinemas,
        physicalRelease = item.physicalRelease,
        digitalRelease = item.digitalRelease,
        images = item.images,
        remotePoster = item.remotePoster,
        year = item.year,
        youTubeTrailerId = item.youTubeTrailerId,
        studio = item.studio,
        runtime = item.runtime,
        imdbId = item.imdbId,
        tmdbId = item.tmdbId,
        certification = item.certification,
        genres = item.genres,
        ratings = item.ratings,
    )

    /**
     * Library movie ids belonging to one TMDB collection that still miss a
     * file; these are the ids the search command targets. Server-assigned
     * movies keep their id, optimistic offline adds carry id 0 and cannot be
     * searched yet.
     */
    fun missingMovieIds(movies: List<MovieResource>, collectionTmdbId: Int): List<Int> = movies
        .filter { it.collection?.tmdbId == collectionTmdbId && it.hasFile != true && it.id != 0 }
        .map { it.id }
        .sorted()

    /**
     * Builds the POST /api/v3/movie body for one add, mirroring the web
     * client: the lookup result supplies the metadata, the form supplies the
     * settings, and `addOptions.monitor` plus `monitored` encode the chosen
     * monitoring strategy.
     */
    fun toAddMoviePayload(
        candidate: MovieResource,
        rootFolderPath: String,
        qualityProfileId: Int,
        monitor: MonitorTypes,
        minimumAvailability: MovieStatusType,
        tags: Set<Int>,
        searchForMovie: Boolean,
    ): MovieResource = candidate.copy(
        id = 0,
        monitored = monitor != MonitorTypes.NONE,
        qualityProfileId = qualityProfileId,
        rootFolderPath = rootFolderPath,
        minimumAvailability = minimumAvailability,
        tags = tags,
        sizeOnDisk = null,
        hasFile = null,
        movieFile = null,
        statistics = null,
        addOptions = AddMovieOptions(
            monitor = monitor,
            searchForMovie = searchForMovie,
            addMethod = AddMovieMethod.MANUAL,
        ),
    )

    /** True when a 4-digit token is a plausible release year; used by UI text only. */
    fun looksLikeYear(token: String): Boolean = YEARS.matches(token)
}

/** Discovery feed split by the server's flags; every list may be empty. */
data class DiscoverySections(
    val recommended: List<ImportListMoviesResource>,
    val trending: List<ImportListMoviesResource>,
    val popular: List<ImportListMoviesResource>,
) {
    val isEmpty: Boolean get() = recommended.isEmpty() && trending.isEmpty() && popular.isEmpty()
}
