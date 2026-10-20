package dev.appolon.homelab.arr.data.store

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * [ArrDatabase] over androidx.sqlite's [SupportSQLiteDatabase]. The catalog
 * has no framework open-helper artifact, so production opens databases via
 * [PlatformArrDatabase]; this adapter serves callers that already hold a
 * SupportSQLiteDatabase (for example handed over by Room elsewhere in the
 * app) and keeps the store usable on androidx.sqlite types.
 */
internal class SupportArrDatabase(
    private val db: SupportSQLiteDatabase,
) : ArrDatabase {

    override fun exec(sql: String) = db.execSQL(sql)

    override fun execute(sql: String, args: List<Any?>) {
        db.execSQL(sql, args.toTypedArray())
    }

    override fun <R> inTransaction(block: () -> R): R {
        db.beginTransaction()
        try {
            val result = block()
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
        }
    }

    override fun query(sql: String, args: List<Any?>): List<Array<Any?>> =
        db.query(sql, args.toTypedArray()).use { cursor ->
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
            rows
        }

    override fun close() = db.close()
}
