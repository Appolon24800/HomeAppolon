package dev.appolon.homelab.arr.data.store

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

/**
 * [ArrDatabase] on the platform SQLite driver, opened directly on the file
 * from [DatabaseLocation]. The catalog's androidx.sqlite artifact ships only
 * interface-level types (no framework open-helper factory), so this is the
 * production route; schema migration runs from [ArrDbMigrations] on open.
 */
internal class PlatformArrDatabase private constructor(
    private val db: SQLiteDatabase,
) : ArrDatabase {

    override fun exec(sql: String) = db.execSQL(sql)

    override fun execute(sql: String, args: List<Any?>) {
        db.execSQL(sql, args.toTypedArray())
    }

    override fun <R> inTransaction(block: () -> R): R {
        // Platform SQLite handles nested beginTransaction with an internal
        // stack, so the outermost successful commit wins as required.
        db.beginTransaction()
        try {
            val result = block()
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
        }
    }

    override fun query(sql: String, args: List<Any?>): List<Array<Any?>> {
        // rawQuery binds strings; INTEGER column affinity converts numeric
        // text back to integers on storage and comparison.
        val bindArgs = args.map { it?.toString() }.toTypedArray()
        db.rawQuery(sql, bindArgs).use { cursor ->
            val rows = ArrayList<Array<Any?>>(cursor.count)
            while (cursor.moveToNext()) {
                val row = arrayOfNulls<Any?>(cursor.columnCount)
                for (index in row.indices) {
                    row[index] = when (cursor.getType(index)) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                        Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(index)
                        else -> cursor.getString(index)
                    }
                }
                rows.add(row)
            }
            return rows
        }
    }

    override fun close() = db.close()

    companion object {
        /** Opens (creating or migrating as needed) the database at [location]. */
        fun open(location: DatabaseLocation): PlatformArrDatabase {
            val file = location.file()
            file.parentFile?.mkdirs()
            val db = SQLiteDatabase.openOrCreateDatabase(file, null)
            try {
                ensureSchema(db)
            } catch (failure: RuntimeException) {
                db.close()
                throw failure
            }
            return PlatformArrDatabase(db)
        }

        /**
         * PRAGMA user_version carries the schema version. Version 0 is either
         * a fresh database or one predating versioning; the v1 statements use
         * IF NOT EXISTS, so running them on an existing table is a no-op.
         */
        private fun ensureSchema(db: SQLiteDatabase) {
            val current = db.version
            if (current >= ArrDbMigrations.VERSION) return
            db.beginTransaction()
            try {
                ArrDbMigrations.statementsFor(current).forEach(db::execSQL)
                db.version = ArrDbMigrations.VERSION
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }
}
