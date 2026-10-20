package dev.appolon.homelab.arr.data.store

/**
 * Schema history for arr.db, keyed by PRAGMA user_version. Append-only: never
 * edit or reorder an entry that shipped, never remove its statements, only
 * append with the next [ArrDbMigration.toVersion].
 */
internal object ArrDbMigrations {

    internal val MIGRATIONS: List<ArrDbMigration> = listOf(
        ArrDbMigration(
            toVersion = 1,
            statements = listOf(EntitySql.CREATE_TABLE, EntitySql.CREATE_INDEX),
        ),
    )

    /** Current schema version; valid because versions stay contiguous from 1. */
    val VERSION: Int get() = MIGRATIONS.size

    /** Statements needed to move a database from [currentVersion] to [VERSION]. */
    fun statementsFor(currentVersion: Int): List<String> =
        MIGRATIONS.filter { it.toVersion > currentVersion }.flatMap { it.statements }
}

internal data class ArrDbMigration(
    val toVersion: Int,
    val statements: List<String>,
)
