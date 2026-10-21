package dev.appolon.homelab.arr.feature.lidarr.addnew.data

import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.core.ActionId

/** How an add or studio apply landed: applied on the server, or queued for replay. */
sealed interface AddOutcome {
    data object Synced : AddOutcome
    data class Queued(val actionId: ActionId) : AddOutcome
}

/** Both lookup flavors in one value, so the results area renders one LCE. */
data class SearchResults(
    val artists: List<ArtistResource> = emptyList(),
    val albums: List<AlbumResource> = emptyList(),
) {
    val isEmpty: Boolean get() = artists.isEmpty() && albums.isEmpty()
}
