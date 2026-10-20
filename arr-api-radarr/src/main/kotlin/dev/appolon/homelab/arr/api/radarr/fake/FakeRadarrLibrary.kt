package dev.appolon.homelab.arr.api.radarr.fake

import dev.appolon.homelab.arr.api.radarr.api.AlternativeTitleApi
import dev.appolon.homelab.arr.api.radarr.api.CreditApi
import dev.appolon.homelab.arr.api.radarr.api.ExtraFileApi
import dev.appolon.homelab.arr.api.radarr.api.MovieApi
import dev.appolon.homelab.arr.api.radarr.api.MovieEditorApi
import dev.appolon.homelab.arr.api.radarr.api.MovieFileApi
import dev.appolon.homelab.arr.api.radarr.api.MovieFolderApi
import dev.appolon.homelab.arr.api.radarr.api.MovieImportApi
import dev.appolon.homelab.arr.api.radarr.api.MovieLookupApi
import dev.appolon.homelab.arr.api.radarr.api.ReleaseApi
import dev.appolon.homelab.arr.api.radarr.model.ReleaseResource
import dev.appolon.homelab.arr.api.radarr.model.RenameMovieResource
import dev.appolon.homelab.arr.api.radarr.model.AlternativeTitleResource
import dev.appolon.homelab.arr.api.radarr.model.ApplyTags
import dev.appolon.homelab.arr.api.radarr.model.CreditResource
import dev.appolon.homelab.arr.api.radarr.model.ExtraFileResource
import dev.appolon.homelab.arr.api.radarr.model.MovieEditorResource
import dev.appolon.homelab.arr.api.radarr.model.MovieFileListResource
import dev.appolon.homelab.arr.api.radarr.model.MovieFileResource
import dev.appolon.homelab.arr.api.radarr.model.MovieFolderResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import retrofit2.HttpException
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of every interface this module owns, for UI tests.
 *
 * The interfaces cannot be implemented on the class itself: five of them
 * declare `get(id: Int)` with unrelated return types, which is a JVM
 * declaration clash in a single class. Each is therefore served by an inner
 * implementation sharing one [State], and exposed as a property so tests
 * and preview view models wire the fake exactly like the Retrofit interface
 * it replaces.
 *
 * Behavior knobs, safe to flip mid-test:
 *  - [latencyMs] delays every call through [sleep], as a slow server would;
 *  - [error] fails every subsequent call once set;
 *  - [errorOnce] fails exactly the next call, then clears itself.
 *
 * [sleep] is injectable because this module cannot depend on
 * kotlinx-coroutines on the main classpath (the catalog only ships the test
 * artifact); unit tests pass `{ delay(it) }` from inside `runTest` so virtual
 * time still advances, and Compose UI tests can use a real dispatcher.
 *
 * Divergences from the real server, kept deliberate: `movieFile.list` with no
 * filter returns everything instead of a 400 (a UI never sends that request),
 * and `movieLookup.lookup` searches only the seeded [lookupCatalog] because
 * the real endpoint hits TMDB.
 */
class FakeRadarrLibrary(
    latencyMs: Long = 0L,
    error: Throwable? = null,
    private val sleep: suspend (Long) -> Unit = {},
) {
    private val state = State(latencyMs = latencyMs, error = error, sleep = sleep)

    val movie: MovieApi = MovieApiFake(state)
    val releases: MutableList<ReleaseResource> get() = state.releases
    val grabbedReleases: MutableList<ReleaseResource> get() = state.grabbedReleases
    val renames: MutableList<RenameMovieResource> get() = state.renames
    val release: ReleaseApi = object : ReleaseApi {
        override suspend fun list(movieId: Int?): List<ReleaseResource> = state.gate().let {
            state.releases.filter { movieId == null || it.movieId == movieId || it.mappedMovieId == movieId }
        }

        override suspend fun grab(release: ReleaseResource): Response<ReleaseResource> = state.gate().let {
            state.grabbedReleases += release
            Response.success(release)
        }

        override suspend fun push(release: ReleaseResource): List<ReleaseResource> = state.gate().let {
            state.releases += release
            listOf(release)
        }
    }
    val movieEditor: MovieEditorApi = MovieEditorApiFake(state)
    val movieFile: MovieFileApi = MovieFileApiFake(state)
    val movieFolder: MovieFolderApi = MovieFolderApiFake(state)
    val movieImport: MovieImportApi = MovieImportApiFake(state)
    val movieLookup: MovieLookupApi = MovieLookupApiFake(state)
    val alternativeTitle: AlternativeTitleApi = AlternativeTitleApiFake(state)
    val credit: CreditApi = CreditApiFake(state)
    val extraFile: ExtraFileApi = ExtraFileApiFake(state)

    /** Library contents. Seed before the test, assert after it. */
    val movies: MutableList<MovieResource> get() = state.movies
    val movieFiles: MutableList<MovieFileResource> get() = state.movieFiles
    val alternativeTitles: MutableList<AlternativeTitleResource> get() = state.alternativeTitles
    val credits: MutableList<CreditResource> get() = state.credits
    val extraFiles: MutableList<ExtraFileResource> get() = state.extraFiles

    /** TMDB-shaped search results served by [MovieLookupApiFake]. */
    val lookupCatalog: MutableList<MovieResource> get() = state.lookupCatalog

    /** TMDB ids recorded through `addImportExclusion=true` deletes. */
    val importExclusions: MutableSet<Int> get() = state.importExclusions

    var latencyMs: Long
        get() = state.latencyMs
        set(value) {
            state.latencyMs = value
        }

    /** When set, every call fails with this error until cleared. */
    var error: Throwable?
        get() = state.error
        set(value) {
            state.error = value
        }

    /** When set, exactly the next call fails, then this clears itself. */
    var errorOnce: Throwable?
        get() = state.errorOnce
        set(value) {
            state.errorOnce = value
        }

    private class State(
        var latencyMs: Long,
        var error: Throwable?,
        var errorOnce: Throwable? = null,
        private val sleep: suspend (Long) -> Unit = {},
    ) {
        val movies = mutableListOf<MovieResource>()
        val releases = mutableListOf<ReleaseResource>()
        val grabbedReleases = mutableListOf<ReleaseResource>()
        val renames = mutableListOf<RenameMovieResource>()
        val movieFiles = mutableListOf<MovieFileResource>()
        val alternativeTitles = mutableListOf<AlternativeTitleResource>()
        val credits = mutableListOf<CreditResource>()
        val extraFiles = mutableListOf<ExtraFileResource>()
        val lookupCatalog = mutableListOf<MovieResource>()
        val importExclusions = mutableSetOf<Int>()

        // Id counters are checked-put style so parallel UI tests never share a collision.
        val ids = ConcurrentHashMap.newKeySet<Int>()
        var nextId = 1

        fun freshId(): Int = synchronized(this) {
            while (!ids.add(nextId)) nextId++
            nextId
        }

        suspend fun gate() {
            if (latencyMs > 0) sleep(latencyMs)
            errorOnce?.let {
                errorOnce = null
                throw it
            }
            error?.let { throw it }
        }
    }

    private companion object {
        val EMPTY_BODY = "".toResponseBody("application/json".toMediaTypeOrNull())
        fun httpNotFound(): Nothing = throw HttpException(notFoundResponse<Unit>())
        fun <T> notFoundResponse(): Response<T> = Response.error(404, EMPTY_BODY)
    }

    private class MovieApiFake(private val s: State) : MovieApi {
        override suspend fun list(
            tmdbId: Int?,
            excludeLocalCovers: Boolean?,
            languageId: Int?,
        ): List<MovieResource> = s.gate().let {
            s.movies
                .filter { tmdbId == null || it.tmdbId == tmdbId }
                .map { m ->
                    val local = m.images?.filter { img -> !img.url.orEmpty().startsWith('/') }
                    if (excludeLocalCovers != true) m else m.copy(images = local)
                }
        }

        override suspend fun get(id: Int): Response<MovieResource> = s.gate().let {
            s.movies.find { it.id == id }?.let { Response.success(it) } ?: notFoundResponse()
        }

        override suspend fun create(movie: MovieResource): Response<MovieResource> = s.gate().let {
            if (movie.tmdbId == 0) return Response.error(404, EMPTY_BODY)
            val stored = if (movie.id == 0) movie.copy(id = s.freshId()) else movie
            s.movies += stored
            Response.success(201, stored)
        }

        override suspend fun update(
            id: String,
            moveFiles: Boolean?,
            movie: MovieResource,
        ): Response<MovieResource> = s.gate().let {
            val index = s.movies.indexOfFirst { it.id.toString() == id }
            if (index < 0) return notFoundResponse()
            val updated = movie.copy(id = s.movies[index].id)
            s.movies[index] = updated
            Response.success(202, updated)
        }

        override suspend fun delete(
            id: Int,
            deleteFiles: Boolean?,
            addImportExclusion: Boolean?,
        ) = s.gate().let {
            val removed = s.movies.find { it.id == id } ?: return Unit
            s.movies -= removed
            if (addImportExclusion == true) s.importExclusions += removed.tmdbId
            if (deleteFiles == true) s.movieFiles.removeAll { it.movieId == id }
            Unit
        }
    }

    private class MovieEditorApiFake(private val s: State) : MovieEditorApi {
        override suspend fun update(editor: MovieEditorResource): Response<List<MovieResource>> =
            s.gate().let {
                if (editor.applyTags != null && editor.tags == null) {
                    return Response.error(400, EMPTY_BODY)
                }
                val newTags = editor.tags.orEmpty()
                val updated = s.movies.map { m ->
                    if (m.id !in (editor.movieIds ?: emptyList())) {
                        m
                    } else {
                        val tags = when (editor.applyTags) {
                            ApplyTags.ADD -> (m.tags ?: emptySet()) + newTags
                            ApplyTags.REMOVE -> (m.tags ?: emptySet()) - newTags.toSet()
                            ApplyTags.REPLACE -> newTags.toSet()
                            null -> m.tags
                        }
                        val availability = editor.minimumAvailability ?: m.minimumAvailability
                        m.copy(
                            monitored = editor.monitored ?: m.monitored,
                            qualityProfileId = editor.qualityProfileId ?: m.qualityProfileId,
                            minimumAvailability = availability,
                            rootFolderPath = editor.rootFolderPath ?: m.rootFolderPath,
                            tags = tags,
                        )
                    }
                }
                s.movies.clear()
                s.movies += updated
                val touched = updated.filter { it.id in (editor.movieIds ?: emptyList()) }
                Response.success(touched)
            }

        override suspend fun delete(editor: MovieEditorResource) = s.gate().let {
            val ids = editor.movieIds?.toSet() ?: emptySet()
            val removed = s.movies.filter { it.id in ids }
            s.movies.removeAll { it.id in ids }
            if (editor.addImportExclusion) s.importExclusions += removed.map { it.tmdbId }
            if (editor.deleteFiles) s.movieFiles.removeAll { it.movieId in ids }
            Unit
        }
    }

    private class MovieFileApiFake(private val s: State) : MovieFileApi {
        override suspend fun rename(movieId: List<Int>?): List<RenameMovieResource> = s.gate().let {
            s.renames.filter { movieId == null || it.movieId in movieId }
        }

        override suspend fun list(
            movieId: List<Int>?,
            movieFileIds: List<Int>?,
        ): List<MovieFileResource> = s.gate().let {
            s.movieFiles.filter { f ->
                (movieId == null || f.movieId in movieId) &&
                    (movieFileIds == null || f.id in movieFileIds)
            }
        }

        override suspend fun get(id: Int): Response<MovieFileResource> = s.gate().let {
            s.movieFiles.find { it.id == id }?.let { Response.success(it) } ?: notFoundResponse()
        }

        override suspend fun update(
            id: String,
            movieFile: MovieFileResource,
        ): Response<MovieFileResource> = s.gate().let {
            val index = s.movieFiles.indexOfFirst { it.id.toString() == id }
            if (index < 0) return notFoundResponse()
            val updated = movieFile.copy(id = s.movieFiles[index].id)
            s.movieFiles[index] = updated
            Response.success(202, updated)
        }

        override suspend fun delete(id: Int): Response<Unit> = s.gate().let {
            if (s.movieFiles.removeAll { it.id == id }) Response.success(Unit)
            else notFoundResponse()
        }

        override suspend fun updateBulk(
            movieFiles: List<MovieFileResource>,
        ): List<MovieFileResource> = s.gate().let {
            val updated = movieFiles.map { incoming ->
                val index = s.movieFiles.indexOfFirst { it.id == incoming.id }
                if (index >= 0) {
                    s.movieFiles[index] = incoming
                    incoming
                } else {
                    s.movieFiles += incoming
                    incoming
                }
            }
            updated
        }

        override suspend fun deleteBulk(files: MovieFileListResource) = s.gate().let {
            val ids = files.movieFileIds?.toSet() ?: emptySet()
            s.movieFiles.removeAll { it.id in ids }
            Unit
        }

        @Deprecated("Radarr deprecated the moviefile editor endpoint; use updateBulk")
        override suspend fun updateEditor(files: MovieFileListResource): List<MovieFileResource> =
            s.gate().let {
                val ids = files.movieFileIds?.toSet() ?: emptySet()
                s.movieFiles.map { f ->
                    if (f.id !in ids) f
                    else f.copy(
                        languages = files.languages ?: f.languages,
                        quality = files.quality ?: f.quality,
                        edition = files.edition ?: f.edition,
                        releaseGroup = files.releaseGroup ?: f.releaseGroup,
                        sceneName = files.sceneName ?: f.sceneName,
                        indexerFlags = files.indexerFlags ?: f.indexerFlags,
                    )
                }
            }
    }

    private class MovieFolderApiFake(private val s: State) : MovieFolderApi {
        override suspend fun get(id: Int): MovieFolderResource = s.gate().let {
            val movie = s.movies.find { it.id == id } ?: httpNotFound()
            val folder = movie.folder ?: movie.folderName
                ?: movie.path?.substringAfterLast('/')
            MovieFolderResource(folder = folder)
        }
    }

    private class MovieImportApiFake(private val s: State) : MovieImportApi {
        override suspend fun importMovies(movies: List<MovieResource>): List<MovieResource> =
            s.gate().let {
                movies.map { m ->
                    val stored = if (m.id == 0) m.copy(id = s.freshId()) else m
                    s.movies += stored
                    stored
                }
            }
    }

    private class MovieLookupApiFake(private val s: State) : MovieLookupApi {
        override suspend fun lookup(term: String?): List<MovieResource> = s.gate().let {
            val needle = term?.trim()?.lowercase() ?: return@let emptyList()
            s.lookupCatalog.filter { it.title?.lowercase()?.contains(needle) == true }
        }

        override suspend fun lookupByTmdbId(tmdbId: Int?): MovieResource = s.gate().let {
            (s.lookupCatalog + s.movies).find { it.tmdbId == tmdbId } ?: httpNotFound()
        }

        override suspend fun lookupByImdbId(imdbId: String?): MovieResource = s.gate().let {
            (s.lookupCatalog + s.movies).find { it.imdbId == imdbId } ?: httpNotFound()
        }
    }

    private class AlternativeTitleApiFake(private val s: State) : AlternativeTitleApi {
        override suspend fun list(
            movieId: Int?,
            movieMetadataId: Int?,
        ): List<AlternativeTitleResource> = s.gate().let {
            s.alternativeTitles.filter { t ->
                (movieId == null || t.movieMetadataId == movieId) &&
                    (movieMetadataId == null || t.movieMetadataId == movieMetadataId)
            }
        }

        override suspend fun get(id: Int): Response<AlternativeTitleResource> = s.gate().let {
            val found = s.alternativeTitles.find { it.id == id }
            found?.let { Response.success(it) } ?: notFoundResponse()
        }
    }

    private class CreditApiFake(private val s: State) : CreditApi {
        override suspend fun list(
            movieId: Int?,
            movieMetadataId: Int?,
        ): List<CreditResource> = s.gate().let {
            s.credits.filter { c ->
                (movieId == null || c.movieMetadataId == movieId) &&
                    (movieMetadataId == null || c.movieMetadataId == movieMetadataId)
            }
        }

        override suspend fun get(id: Int): Response<CreditResource> = s.gate().let {
            s.credits.find { it.id == id }?.let { Response.success(it) } ?: notFoundResponse()
        }
    }

    private class ExtraFileApiFake(private val s: State) : ExtraFileApi {
        override suspend fun list(movieId: Int?): List<ExtraFileResource> = s.gate().let {
            s.extraFiles.filter { movieId == null || it.movieId == movieId }
        }
    }
}
