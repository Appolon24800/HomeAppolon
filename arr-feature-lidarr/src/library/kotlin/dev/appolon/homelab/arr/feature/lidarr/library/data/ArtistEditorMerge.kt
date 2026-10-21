package dev.appolon.homelab.arr.feature.lidarr.library.data

import dev.appolon.homelab.arr.api.lidarr.model.ApplyTags
import dev.appolon.homelab.arr.api.lidarr.model.ArtistEditorResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource

/**
 * Client-side application of an [ArtistEditorResource] to cached artists,
 * mirroring the server's semantics field by field: null fields stay untouched
 * and `applyTags` merges rather than replaces unless it says REPLACE. Used for
 * optimistic state while a bulk edit sits in the offline queue, so the library
 * reads as already edited and reconciles when the replay lands. The
 * non-nullable `monitorNewItems` follows the payload verbatim; the ViewModel
 * resolves "unchanged" into the targets' current value before building it.
 */
fun applyArtistEditor(artists: List<ArtistResource>, editor: ArtistEditorResource): List<ArtistResource> {
    val ids = editor.artistIds.orEmpty().toSet()
    if (ids.isEmpty()) return artists
    return artists.map { artist ->
        if (artist.id !in ids) {
            artist
        } else {
            artist.copy(
                monitored = editor.monitored ?: artist.monitored,
                monitorNewItems = editor.monitorNewItems,
                qualityProfileId = editor.qualityProfileId ?: artist.qualityProfileId,
                metadataProfileId = editor.metadataProfileId ?: artist.metadataProfileId,
                rootFolderPath = editor.rootFolderPath ?: artist.rootFolderPath,
                tags = mergeTags(artist.tags, editor),
            )
        }
    }
}

private fun mergeTags(current: List<Int>?, editor: ArtistEditorResource): List<Int>? {
    val incoming = editor.tags ?: return current
    return when (editor.applyTags) {
        ApplyTags.ADD -> (current ?: emptyList()) + incoming
        ApplyTags.REMOVE -> (current ?: emptyList()) - incoming.toSet()
        ApplyTags.REPLACE -> incoming
    }.distinct()
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
