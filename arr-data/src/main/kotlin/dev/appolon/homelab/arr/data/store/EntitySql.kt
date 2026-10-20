package dev.appolon.homelab.arr.data.store

/**
 * Every statement the entity store runs, as verbatim strings so tests can pin
 * the exact SQL grammar and the "?" argument order.
 */
internal object EntitySql {
    const val CREATE_TABLE =
        "CREATE TABLE IF NOT EXISTS entities (" +
            "serverId TEXT NOT NULL, " +
            "type TEXT NOT NULL, " +
            "id TEXT NOT NULL, " +
            "json TEXT NOT NULL, " +
            "fetchedAt INTEGER NOT NULL, " +
            "PRIMARY KEY (serverId, type, id))"

    const val CREATE_INDEX =
        "CREATE INDEX IF NOT EXISTS index_entities_serverId_type ON entities (serverId, type)"

    const val UPSERT =
        "INSERT OR REPLACE INTO entities (serverId, type, id, json, fetchedAt) " +
            "VALUES (?, ?, ?, ?, ?)"

    /** Ordered by id so refreshes (which replace rows) yield stable list output. */
    const val SELECT_BY_SCOPE =
        "SELECT serverId, type, id, json, fetchedAt FROM entities " +
            "WHERE serverId = ? AND type = ? ORDER BY id"

    const val SELECT_ONE =
        "SELECT json FROM entities WHERE serverId = ? AND type = ? AND id = ?"

    const val DELETE_ONE =
        "DELETE FROM entities WHERE serverId = ? AND type = ? AND id = ?"

    const val DELETE_SCOPE =
        "DELETE FROM entities WHERE serverId = ? AND type = ?"

    const val DELETE_SERVER =
        "DELETE FROM entities WHERE serverId = ?"

    const val MAX_FETCHED_AT =
        "SELECT MAX(fetchedAt) FROM entities WHERE serverId = ? AND type = ?"

    /** [EntityStore.deleteAll] with a null type deletes every type for the server. */
    fun deleteAllFor(type: String?): String = if (type == null) DELETE_SERVER else DELETE_SCOPE
}

/**
 * One materialized `entities` row, in [EntitySql.SELECT_BY_SCOPE] column
 * order. Separate from the store so mapping is testable on its own.
 */
internal data class EntityRow(
    val serverId: String,
    val type: String,
    val id: String,
    val json: String,
    val fetchedAtMillis: Long,
)

internal fun EntityRow(row: Array<Any?>): EntityRow = EntityRow(
    serverId = row[0] as String,
    type = row[1] as String,
    id = row[2] as String,
    json = row[3] as String,
    // SQLite may surface INTEGER columns as Int depending on the driver.
    fetchedAtMillis = (row[4] as Number).toLong(),
)
