package dev.appolon.homelab.arr.feature.radarr.library.data

import dev.appolon.homelab.arr.api.radarr.model.ApplyTags
import dev.appolon.homelab.arr.api.radarr.model.MovieEditorResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource

/**
 * Client-side application of a [MovieEditorResource] to cached movies,
 * mirroring the server's semantics field by field: null fields stay untouched
 * and `applyTags` merges rather than replaces unless it says REPLACE. Used for
 * optimistic state while a bulk edit sits in the offline queue, so the library
 * reads as already edited and reconciles when the replay lands.
 */
fun applyMovieEditor(
    movies: List<MovieResource>,
    editor: MovieEditorResource,
): List<MovieResource> {
    val ids = editor.movieIds.orEmpty().toSet()
    if (ids.isEmpty()) return movies
    return movies.map { movie ->
        if (movie.id !in ids) {
            movie
        } else {
            movie.copy(
                monitored = editor.monitored ?: movie.monitored,
                qualityProfileId = editor.qualityProfileId ?: movie.qualityProfileId,
                minimumAvailability = editor.minimumAvailability ?: movie.minimumAvailability,
                rootFolderPath = editor.rootFolderPath ?: movie.rootFolderPath,
                tags = mergeTags(movie.tags, editor),
            )
        }
    }
}

private fun mergeTags(current: Set<Int>?, editor: MovieEditorResource): Set<Int>? {
    val incoming = editor.tags?.toSet() ?: return current
    return when (editor.applyTags) {
        ApplyTags.ADD -> (current ?: emptySet()) + incoming
        ApplyTags.REMOVE -> (current ?: emptySet()) - incoming
        ApplyTags.REPLACE, null -> incoming
    }
}

/**
 * Parses the tag entry of the editor form: integers separated by commas or
 * whitespace. Returns null when any token is not a number so the form can ask
 * again instead of silently dropping tags.
 */
fun parseTagIds(raw: String): List<Int>? {
    val tokens = raw.split(',', ' ', ';').filter { it.isNotBlank() }
    if (tokens.isEmpty()) return emptyList()
    return tokens.map { token -> token.trim().toIntOrNull() ?: return null }
}
