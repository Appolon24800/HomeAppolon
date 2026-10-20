package dev.appolon.homelab.arr.net

import kotlinx.serialization.json.Json

/**
 * The one shared [Json] instance for every arr module, importable as `ArrJson`
 * wherever a [Json] is expected, including Retrofit's kotlinx converter. Wire
 * DTOs are lenient by design: servers add fields between releases, nulls are
 * omitted on the wire rather than defaulted around, and defaults are encoded so
 * queued actions round-trip losslessly through the entity store.
 */
val ArrJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    encodeDefaults = true
}
