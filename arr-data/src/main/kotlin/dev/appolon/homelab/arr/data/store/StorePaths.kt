package dev.appolon.homelab.arr.data.store

import java.io.File

/**
 * Where arr.db lives. Injected as an interface so production points the graph
 * at the app's private storage while tests point at temp directories without
 * touching Android.
 */
interface DatabaseLocation {
    fun file(): java.io.File
}

/** Standard [DatabaseLocation]: <directory>/arr.db. */
class DirectoryDatabaseLocation(directory: File) : DatabaseLocation {
    private val databaseDirectory = directory.apply { mkdirs() }
    override fun file(): File = File(databaseDirectory, StorePaths.DATABASE_NAME)
}

/**
 * Filesystem layout for every :arr-data store under one base directory:
 * arr.db, the wrapped secrets key file, and the DataStore preferences file.
 */
class StorePaths(baseDirectory: File) {
    private val root = File(baseDirectory, ROOT_NAME).apply { mkdirs() }

    val database: DatabaseLocation = DirectoryDatabaseLocation(root)

    /** Parent for the wrapped key file used by KeystoreArrSecrets. */
    val secretsDirectory: File
        get() = File(root, SECRETS_DIR)

    val preferencesFile: File
        get() = File(root, PREFERENCES_NAME)

    companion object {
        const val DATABASE_NAME = "arr.db"
        private const val ROOT_NAME = "arr"
        private const val SECRETS_DIR = "secrets"
        private const val PREFERENCES_NAME = "arr_settings.preferences_pb"
    }
}
