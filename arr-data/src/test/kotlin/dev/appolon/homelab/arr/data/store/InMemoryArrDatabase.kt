package dev.appolon.homelab.arr.data.store

/**
 * In-memory [ArrDatabase] that executes the store's known statement shapes
 * against a map, so argument order and SQL text are validated by every test
 * that goes through the store. Any unexpected SQL fails the test loudly.
 */
internal class InMemoryArrDatabase : ArrDatabase {
    val executedStatements = mutableListOf<Pair<String, List<Any?>>>()
    val executedDdl = mutableListOf<String>()
    val rows = LinkedHashMap<RowKey, Array<Any?>>()

    private val lock = Any()

    override fun exec(sql: String) {
        synchronized(lock) { executedDdl += sql }
    }

    override fun execute(sql: String, args: List<Any?>) {
        synchronized(lock) {
            executedStatements += sql to args
            when (sql) {
                EntitySql.UPSERT -> rows[rowKey(args)] = args.toTypedArray()
                EntitySql.DELETE_ONE -> rows.remove(rowKey(args))
                EntitySql.DELETE_SCOPE -> removeMatching { key ->
                    key.first == args[0] && key.second == args[1]
                }
                EntitySql.DELETE_SERVER -> removeMatching { key -> key.first == args[0] }
                else -> error("unexpected statement: $sql")
            }
        }
    }

    override fun <R> inTransaction(block: () -> R): R = block()

    override fun query(sql: String, args: List<Any?>): List<Array<Any?>> = synchronized(lock) {
        when (sql) {
            EntitySql.SELECT_BY_SCOPE -> rows.values
                .filter { row -> row[0] == args[0] && row[1] == args[1] }
                .sortedBy { row -> row[2] as String }
                .map { row -> row.copyOf() }
            EntitySql.SELECT_ONE -> {
                val row = rows[rowKey(args)]
                if (row == null) emptyList() else listOf(arrayOf(row[3]))
            }
            EntitySql.MAX_FETCHED_AT -> {
                val fetchedAt = rows.values
                    .filter { row -> row[0] == args[0] && row[1] == args[1] }
                    .maxOfOrNull { row -> (row[4] as Number).toLong() }
                listOf(arrayOf(fetchedAt))
            }
            else -> error("unexpected query: $sql")
        }
    }

    override fun close() {}

    private fun removeMatching(predicate: (RowKey) -> Boolean) {
        val doomed = rows.keys.filter(predicate)
        doomed.forEach(rows::remove)
    }

    private fun rowKey(args: List<Any?>): RowKey =
        Triple(args[0] as String, args[1] as String, args[2] as String)
}

internal typealias RowKey = Triple<String, String, String>
