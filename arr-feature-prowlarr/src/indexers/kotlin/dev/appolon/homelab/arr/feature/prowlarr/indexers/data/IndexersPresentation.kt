package dev.appolon.homelab.arr.feature.prowlarr.indexers.data

import dev.appolon.homelab.arr.api.prowlarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerPrivacy
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerStatusResource
import dev.appolon.homelab.arr.core.ArrTime
import dev.appolon.homelab.arr.core.SortSpec
import dev.appolon.homelab.arr.core.sortItems

/** Filter chips of the indexers screen, each with an ALL option. */
enum class ProtocolFilter(val id: String) { ALL("all"), TORRENT("torrent"), USENET("usenet") }

enum class PrivacyFilter(val id: String) { ALL("all"), PUBLIC("public"), SEMI_PRIVATE("semiPrivate"), PRIVATE("private") }

enum class StateFilter(val id: String) { ALL("all"), ENABLED("enabled"), DISABLED("disabled"), THROTTLED("throttled") }

/** Sort ids of the indexers screen. */
object IndexerSorts {
    const val NAME = "name"
    const val PRIORITY = "priority"
    const val PROTOCOL = "protocol"
    const val ADDED = "added"
    val ALL = listOf(NAME, PRIORITY, PROTOCOL, ADDED)
}

/** How the list state filters, searches, and sorts the cached indexers. */
data class IndexerListQuery(
    val search: String = "",
    val protocol: ProtocolFilter = ProtocolFilter.ALL,
    val privacy: PrivacyFilter = PrivacyFilter.ALL,
    val state: StateFilter = StateFilter.ALL,
    val tagId: Int? = null,
    val sortId: String = IndexerSorts.NAME,
    val sortDescending: Boolean = false,
)

/** Pure presentation pass over the indexer list; no Android types. */
object IndexersPresentation {

    /** True when the indexer is cooling down after failures. */
    fun isThrottled(
        indexer: IndexerResource,
        status: IndexerStatusResource?,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val till = ArrTime.parse(status?.disabledTill) ?: return false
        return till.toEpochMilli() > nowMillis
    }

    fun present(
        indexers: List<IndexerResource>,
        statuses: Map<Int, IndexerStatusResource>,
        query: IndexerListQuery,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<IndexerResource> {
        val needle = query.search.trim().lowercase()
        val filtered = indexers.filter { indexer ->
            val matchesSearch = needle.isEmpty() ||
                indexer.name.orEmpty().lowercase().contains(needle) ||
                indexer.implementationName.orEmpty().lowercase().contains(needle)
            val matchesProtocol = query.protocol == ProtocolFilter.ALL ||
                indexer.protocol == when (query.protocol) {
                    ProtocolFilter.TORRENT -> DownloadProtocol.TORRENT
                    ProtocolFilter.USENET -> DownloadProtocol.USENET
                    ProtocolFilter.ALL -> indexer.protocol
                }
            val matchesPrivacy = query.privacy == PrivacyFilter.ALL ||
                indexer.privacy == when (query.privacy) {
                    PrivacyFilter.PUBLIC -> IndexerPrivacy.PUBLIC
                    PrivacyFilter.SEMI_PRIVATE -> IndexerPrivacy.SEMI_PRIVATE
                    PrivacyFilter.PRIVATE -> IndexerPrivacy.PRIVATE
                    PrivacyFilter.ALL -> indexer.privacy
                }
            val enabled = indexer.enable
            val throttled = isThrottled(indexer, statuses[indexer.id], nowMillis)
            val matchesState = when (query.state) {
                StateFilter.ALL -> true
                StateFilter.ENABLED -> enabled && !throttled
                StateFilter.DISABLED -> !enabled
                StateFilter.THROTTLED -> throttled
            }
            val matchesTag = query.tagId == null || query.tagId in indexer.tags.orEmpty()
            matchesSearch && matchesProtocol && matchesPrivacy && matchesState && matchesTag
        }
        return sortItems(filtered, sortSpec(query.sortId), query.sortDescending)
    }

    /** Named sort matching the sort id; explicit type keeps selectors typed. */
    private fun sortSpec(sortId: String): SortSpec<IndexerResource> = when (sortId) {
        IndexerSorts.PRIORITY -> SortSpec.byNullable(IndexerSorts.PRIORITY, { it.priority })
        IndexerSorts.PROTOCOL -> SortSpec.byNullable(IndexerSorts.PROTOCOL, { it.protocol.name })
        IndexerSorts.ADDED -> SortSpec.byNullable(IndexerSorts.ADDED, { it.added })
        else -> SortSpec.byNullable(IndexerSorts.NAME, { it.sortName ?: it.name })
    }
}
