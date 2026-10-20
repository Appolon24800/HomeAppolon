package dev.appolon.homelab.arr.feature.radarr.addnew.data

import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.ArrResult

/** How an add or edit landed: applied on the server, or queued for replay. */
sealed interface AddOutcome {
    data object Synced : AddOutcome
    data class Queued(val actionId: ActionId) : AddOutcome
}

/**
 * Runs a named Radarr command against the server. Implemented over a raw
 * OkHttp POST by the connection layer because command payloads put selection
 * fields such as `movieIds` at the JSON root, a shape the typed
 * `CommandResource` DTO cannot express.
 */
fun interface AddNewCommandRunner {
    suspend fun run(command: String, movieIds: List<Int>?): ArrResult<Unit>
}

/** A search query after year disambiguation: `Batman (1989)` becomes both parts. */
data class ParsedSearchQuery(val term: String, val year: Int?) {
    companion object {
        val EMPTY = ParsedSearchQuery("", null)
    }
}
