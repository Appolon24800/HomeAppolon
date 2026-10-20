package dev.appolon.homelab.arr.api.prowlarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Download protocol an indexer serves, from the spec's DownloadProtocol enum. */
@Serializable
enum class DownloadProtocol {
    @SerialName("unknown") UNKNOWN,
    @SerialName("usenet") USENET,
    @SerialName("torrent") TORRENT,
}

/** Tracker openness, from the spec's IndexerPrivacy enum. */
@Serializable
enum class IndexerPrivacy {
    @SerialName("public") PUBLIC,
    @SerialName("semiPrivate") SEMI_PRIVATE,
    @SerialName("private") PRIVATE,
}

/**
 * One indexer definition (from the schema endpoint) or configured instance.
 * Schema responses carry editable [fields] plus [presets]; saved instances echo
 * server-computed [capabilities] and [status]. [added] and the timestamps
 * inside [status] stay wire-format strings for :arr-core ArrTime to parse.
 */
@Serializable
data class IndexerResource(
    val id: Int = 0,
    val name: String? = null,
    val fields: List<Field>? = null,
    val implementationName: String? = null,
    val implementation: String? = null,
    val configContract: String? = null,
    val infoLink: String? = null,
    val message: ProviderMessage = ProviderMessage(),
    val tags: List<Int>? = null,
    val presets: List<IndexerResource>? = null,
    val indexerUrls: List<String>? = null,
    val legacyUrls: List<String>? = null,
    val definitionName: String? = null,
    val description: String? = null,
    val language: String? = null,
    val encoding: String? = null,
    val enable: Boolean = false,
    val redirect: Boolean = false,
    val supportsRss: Boolean = false,
    val supportsSearch: Boolean = false,
    val supportsRedirect: Boolean = false,
    val supportsPagination: Boolean = false,
    val appProfileId: Int = 0,
    val protocol: DownloadProtocol = DownloadProtocol.UNKNOWN,
    val privacy: IndexerPrivacy = IndexerPrivacy.PUBLIC,
    val capabilities: IndexerCapabilityResource = IndexerCapabilityResource(),
    val priority: Int = 0,
    val downloadClientId: Int = 0,
    val added: String = "",
    val status: IndexerStatusResource = IndexerStatusResource(),
    val sortName: String? = null,
)
