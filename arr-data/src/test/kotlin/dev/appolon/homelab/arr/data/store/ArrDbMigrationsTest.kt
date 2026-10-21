package dev.appolon.homelab.arr.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrDbMigrationsTest {

    @Test
    fun versionsAreContiguousFromOne() {
        assertEquals(
            (1..ArrDbMigrations.VERSION).toList(),
            ArrDbMigrations.MIGRATIONS.map { it.toVersion },
        )
    }

    @Test
    fun freshDatabaseRunsEveryStatementInOrder() {
        val all = ArrDbMigrations.MIGRATIONS.flatMap { it.statements }

        assertEquals(all, ArrDbMigrations.statementsFor(0))
        // v1 must produce the table and its index.
        assertTrue(all.contains(EntitySql.CREATE_TABLE))
        assertTrue(all.contains(EntitySql.CREATE_INDEX))
    }

    @Test
    fun currentVersionRunsNothing() {
        assertTrue(ArrDbMigrations.statementsFor(ArrDbMigrations.VERSION).isEmpty())
    }
}
