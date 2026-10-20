package dev.appolon.homelab.arr.data.store

import org.junit.Assert.assertEquals
import org.junit.Test

class EntitySqlTest {

    @Test
    fun createTablePinsThePrimaryKeyShape() {
        assertEquals(
            "CREATE TABLE IF NOT EXISTS entities (serverId TEXT NOT NULL, " +
                "type TEXT NOT NULL, id TEXT NOT NULL, json TEXT NOT NULL, " +
                "fetchedAt INTEGER NOT NULL, PRIMARY KEY (serverId, type, id))",
            EntitySql.CREATE_TABLE,
        )
    }

    @Test
    fun createIndexCoversTheHotFilter() {
        assertEquals(
            "CREATE INDEX IF NOT EXISTS index_entities_serverId_type ON entities (serverId, type)",
            EntitySql.CREATE_INDEX,
        )
    }

    @Test
    fun upsertBindsFiveArgumentsInColumnOrder() {
        assertEquals(
            "INSERT OR REPLACE INTO entities (serverId, type, id, json, fetchedAt) " +
                "VALUES (?, ?, ?, ?, ?)",
            EntitySql.UPSERT,
        )
        assertEquals(5, EntitySql.UPSERT.count { it == '?' })
    }

    @Test
    fun scopeSelectIsOrderedByIdForStableLists() {
        assertEquals(
            "SELECT serverId, type, id, json, fetchedAt FROM entities " +
                "WHERE serverId = ? AND type = ? ORDER BY id",
            EntitySql.SELECT_BY_SCOPE,
        )
    }

    @Test
    fun pointQueriesBindTheFullKey() {
        assertEquals(
            "SELECT json FROM entities WHERE serverId = ? AND type = ? AND id = ?",
            EntitySql.SELECT_ONE,
        )
        assertEquals(
            "DELETE FROM entities WHERE serverId = ? AND type = ? AND id = ?",
            EntitySql.DELETE_ONE,
        )
        assertEquals(
            "SELECT MAX(fetchedAt) FROM entities WHERE serverId = ? AND type = ?",
            EntitySql.MAX_FETCHED_AT,
        )
    }

    @Test
    fun deleteAllChoosesTheStatementByTypePresence() {
        assertEquals(EntitySql.DELETE_SCOPE, EntitySql.deleteAllFor("radarr.movie"))
        assertEquals(EntitySql.DELETE_SERVER, EntitySql.deleteAllFor(null))
    }

    @Test
    fun rowMapperFollowsTheSelectColumnOrder() {
        val row = EntityRow(
            arrayOf("server-1", "radarr.movie", "42", """{"id":42}""", 17L),
        )

        assertEquals("server-1", row.serverId)
        assertEquals("radarr.movie", row.type)
        assertEquals("42", row.id)
        assertEquals("""{"id":42}""", row.json)
        assertEquals(17L, row.fetchedAtMillis)
    }

    @Test
    fun rowMapperAcceptsIntegerColumnsDeliveredAsInt() {
        // Different drivers surface INTEGER as Int or Long; both must work.
        val row = EntityRow(
            arrayOf("server-1", "type", "id", "{}", 17),
        )
        assertEquals(17L, row.fetchedAtMillis)
        assertEquals("{}", row.json)
    }
}
