package dev.appolon.homelab.arr.data

import kotlinx.serialization.json.Json

/**
 * JSON for everything :arr-data persists: server registry entries, global
 * settings, entity store rows, queued actions. Matches the project-wide
 * conventions from docs/ARR_ARCHITECTURE.md (which names :arr-net's ArrJson as
 * the shared instance once it exists there).
 */
internal val ArrDataJson: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}
