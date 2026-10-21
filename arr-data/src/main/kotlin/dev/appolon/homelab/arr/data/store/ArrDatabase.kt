package dev.appolon.homelab.arr.data.store

/**
 * Minimal relational seam over SQLite. Keeping this interface small is what
 * lets SQL building and row mapping run in JVM tests against an in-memory
 * fake instead of an Android SQLite driver.
 */
internal interface ArrDatabase : AutoCloseable {
    /** Executes one DDL or other unparameterized statement. */
    fun exec(sql: String)

    /** Executes one bound statement; [args] follow the "?" order in [sql]. */
    fun execute(sql: String, args: List<Any?> = emptyList())

    /**
     * Runs [block] atomically. Implementations must tolerate nesting by
     * committing only when the outermost block succeeds.
     */
    fun <R> inTransaction(block: () -> R): R

    /**
     * Runs a SELECT; each row is returned with values typed as stored
     * (String, Long, Double, ByteArray, or null) in SELECT column order.
     */
    fun query(sql: String, args: List<Any?> = emptyList()): List<Array<Any?>>
}
