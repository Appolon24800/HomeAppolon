package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What one indexer query or grab did, from the spec's HistoryEventType enum.
 * [toString] returns the wire name because Retrofit serializes @Query
 * values through the enum's toString fallback, while bodies use @SerialName.
 */
@Serializable
enum class HistoryEventType(private val wireName: String) {
    @SerialName("unknown") UNKNOWN("unknown"),
    @SerialName("releaseGrabbed") RELEASE_GRABBED("releaseGrabbed"),
    @SerialName("indexerQuery") INDEXER_QUERY("indexerQuery"),
    @SerialName("indexerRss") INDEXER_RSS("indexerRss"),
    @SerialName("indexerAuth") INDEXER_AUTH("indexerAuth"),
    @SerialName("indexerInfo") INDEXER_INFO("indexerInfo");

    override fun toString(): String = wireName
}

/**
 * One history event. [date] stays a wire-format string parsed by the UI with
 * :arr-core ArrTime; [data] is the server's free-form event payload whose
 * keys vary per event type.
 */
@Serializable
data class HistoryResource(
    val id: Int = 0,
    val indexerId: Int = 0,
    val date: String? = null,
    val downloadId: String? = null,
    val successful: Boolean = false,
    val eventType: HistoryEventType = HistoryEventType.UNKNOWN,
    val data: Map<String, String?>? = null,
)
