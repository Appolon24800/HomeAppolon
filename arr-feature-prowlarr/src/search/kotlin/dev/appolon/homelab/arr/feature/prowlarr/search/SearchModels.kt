package dev.appolon.homelab.arr.feature.prowlarr.search

import dev.appolon.homelab.arr.api.prowlarr.model.DownloadClientResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerCategory
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerResource
import dev.appolon.homelab.arr.api.prowlarr.model.ReleaseResource
import java.util.Locale
import kotlinx.serialization.Serializable

/** A canonical request also used as the persisted search cache key. */
@Serializable
data class SearchRequest(
    val query: String = "",
    val indexerIds: Set<Int> = emptySet(),
    val categories: Set<Int> = emptySet(),
) {
    internal fun key(): String = listOf(
        query.trim(), indexerIds.sorted().joinToString(","), categories.sorted().joinToString(","),
    ).joinToString("\n")
}

/** Search controls are cached together so they remain usable without a connection. */
@Serializable
data class SearchOptions(
    val indexers: List<IndexerResource> = emptyList(),
    val categories: List<IndexerCategory> = emptyList(),
    val clients: List<DownloadClientResource> = emptyList(),
)

enum class SearchSort { AGE, SIZE, SEEDERS }

/** Prowlarr does not return a quality object. Preserve the release title instead of guessing. */
fun ReleaseResource.qualityLabel(): String? = Regex(
    "(?i)\\b(2160p|1080p|1080i|720p|576p|480p)\\b",
).find(title.orEmpty())?.value

fun ReleaseResource.rowKey(): String =
    "$indexerId:${guid ?: downloadUrl ?: magnetUrl ?: infoHash ?: title ?: id.toString()}"

private fun ReleaseResource.ageInMinutes(): Double = when {
    ageMinutes > 0 -> ageMinutes
    ageHours > 0 -> ageHours * 60
    else -> age * 1440.0
}

fun sortedReleases(rows: List<ReleaseResource>, sort: SearchSort): List<ReleaseResource> =
    when (sort) {
        SearchSort.AGE -> rows.sortedBy { it.ageInMinutes() }
        SearchSort.SIZE -> rows.sortedByDescending { it.size }
        SearchSort.SEEDERS -> rows.sortedWith(
            compareByDescending<ReleaseResource> { it.seeders ?: -1 }.thenBy { it.age },
        )
    }

internal fun sizeLabel(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> String.format(Locale.getDefault(), "%.1f GiB", bytes / 1073741824.0)
    else -> String.format(Locale.getDefault(), "%.1f MiB", bytes / 1048576.0)
}
