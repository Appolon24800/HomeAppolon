package dev.appolon.homelab.arr.feature.prowlarr.history.data

import dev.appolon.homelab.arr.api.prowlarr.model.HistoryResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerResource
import dev.appolon.homelab.arr.core.ArrTime
import java.time.Instant

/**
 * One renderable history event. The server's free-form `data` payload is
 * decoded once here: keys are the camelCase wire names the API serializes
 * (`query`, `grabTitle`, `elapsedTime`, `cached`, `queryResults`, `queryType`,
 * `source`, `host`), and unknown or absent keys simply leave fields null.
 */
data class HistoryRow(
    val id: Int,
    val event: HistoryResource,
    val indexerName: String,
    val occurredAt: Instant?,
    val title: String,
    val elapsedMs: Int?,
    val cached: Boolean?,
    val resultCount: Int?,
    val queryType: String?,
    val source: String?,
    val host: String?,
    val url: String?,
)

/**
 * Pure mapping and filtering of history events; no Android or IO types so the
 * list screen's logic is unit-testable. Rows sort newest first with a
 * deterministic id tiebreak.
 */
object HistoryPresentation {

    private const val KEY_QUERY = "query"
    private const val KEY_GRAB_TITLE = "grabTitle"
    private const val KEY_ELAPSED = "elapsedTime"
    private const val KEY_CACHED = "cached"
    private const val KEY_RESULTS = "queryResults"
    private const val KEY_QUERY_TYPE = "queryType"
    private const val KEY_SOURCE = "source"
    private const val KEY_HOST = "host"
    private const val KEY_URL = "url"

    fun present(
        events: List<HistoryResource>,
        indexers: List<IndexerResource>,
        query: String = "",
    ): List<HistoryRow> {
        val rows = events.map { event -> rowOf(event, indexers) }
        val needle = query.trim()
        val filtered = if (needle.isEmpty()) {
            rows
        } else {
            rows.filter { row -> row.matches(needle) }
        }
        return filtered.sortedWith(
            compareByDescending<HistoryRow> { it.occurredAt ?: Instant.EPOCH }
                .thenByDescending { it.id },
        )
    }

    /** Indexer display name resolved against the cached list, id as fallback. */
    fun indexerName(indexerId: Int, indexers: List<IndexerResource>): String =
        indexers.firstOrNull { it.id == indexerId }?.name ?: "#$indexerId"

    private fun rowOf(event: HistoryResource, indexers: List<IndexerResource>): HistoryRow {
        val data = event.data.orEmpty()
        fun text(key: String): String? = data[key]?.trim()?.takeIf { it.isNotEmpty() }
        return HistoryRow(
            id = event.id,
            event = event,
            indexerName = indexerName(event.indexerId, indexers),
            occurredAt = ArrTime.parse(event.date),
            title = text(KEY_QUERY) ?: text(KEY_GRAB_TITLE).orEmpty(),
            elapsedMs = text(KEY_ELAPSED)?.toIntOrNull(),
            cached = data[KEY_CACHED]?.let { it == "1" || it.equals("true", ignoreCase = true) },
            resultCount = text(KEY_RESULTS)?.toIntOrNull(),
            queryType = text(KEY_QUERY_TYPE),
            source = text(KEY_SOURCE),
            host = text(KEY_HOST),
            url = text(KEY_URL),
        )
    }

    private fun HistoryRow.matches(needle: String): Boolean {
        val lower = needle.lowercase()
        return title.lowercase().contains(lower) ||
            indexerName.lowercase().contains(lower) ||
            queryType?.lowercase()?.contains(lower) == true ||
            source?.lowercase()?.contains(lower) == true ||
            host?.lowercase()?.contains(lower) == true
    }
}
