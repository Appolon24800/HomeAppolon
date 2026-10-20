package dev.appolon.homelab.arr.feature.sonarr.library.data

import dev.appolon.homelab.arr.api.sonarr.model.ApplyTags
import dev.appolon.homelab.arr.api.sonarr.model.SeasonPassResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesEditorResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource

/**
 * Client-side application of a [SeriesEditorResource] to cached series,
 * mirroring the server's editor semantics field by field: null fields stay
 * untouched and `applyTags` merges rather than replaces unless it says
 * REPLACE. `seriesType` and `monitorNewItems` have no null wire form, so the
 * editor sheet always carries the value it will write and this merge applies
 * it unconditionally, matching Sonarr's own editor behavior. Used for
 * optimistic state while a bulk edit sits in the offline queue, so the library
 * reads as already edited and reconciles when the replay lands.
 */
fun applySeriesEditor(series: List<SeriesResource>, editor: SeriesEditorResource): List<SeriesResource> {
    val ids = editor.seriesIds.orEmpty().toSet()
    if (ids.isEmpty()) return series
    return series.map { item ->
        if (item.id !in ids) {
            item
        } else {
            item.copy(
                monitored = editor.monitored ?: item.monitored,
                monitorNewItems = editor.monitorNewItems,
                qualityProfileId = editor.qualityProfileId ?: item.qualityProfileId,
                seriesType = editor.seriesType,
                seasonFolder = editor.seasonFolder ?: item.seasonFolder,
                rootFolderPath = editor.rootFolderPath ?: item.rootFolderPath,
                tags = mergeTags(item.tags, editor),
            )
        }
    }
}

private fun mergeTags(current: Set<Int>?, editor: SeriesEditorResource): Set<Int>? {
    val incoming = (editor.tags ?: return current).toSet()
    return when (editor.applyTags) {
        ApplyTags.ADD -> (current ?: emptySet()) + incoming
        ApplyTags.REMOVE -> (current ?: emptySet()) - incoming
        ApplyTags.REPLACE -> incoming
    }
}

/**
 * Client-side application of a season pass to cached series. The seasons a
 * season pass carries are trusted as written; monitoring options such as
 * "missing" recompute per-episode state on the server only, so the optimistic
 * merge covers series-level monitored plus season flags and the next refresh
 * brings the episode-derived statistics back in line.
 */
fun applySeasonPass(series: List<SeriesResource>, pass: SeasonPassResource): List<SeriesResource> {
    val byId = pass.series.orEmpty().associateBy { it.id }
    if (byId.isEmpty()) return series
    return series.map { item ->
        val entry = byId[item.id] ?: return@map item
        item.copy(
            monitored = entry.monitored ?: item.monitored,
            seasons = entry.seasons ?: item.seasons,
        )
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
