package de.davis.keygo.legacy_migration.data.local.datasource

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.SQLiteDriver
import de.davis.keygo.legacy_migration.data.local.migration.LegacyMigration2To3

/**
 * Opens the inherited file. Never creates one.
 *
 * The rows that would abort the 2-to-3 recreate are dealt with inside [LegacyMigration2To3] rather
 * than by a pass over the closed file, so the only thing left for this class to guard is the path.
 *
 * @param driver left null in production so Room keeps the framework helper it has always used here.
 * A JVM test has no framework helper to use, so it passes a bundled driver; without one, Room
 * cannot create a database file at all and a test asserting that no file appears could not fail.
 */
internal class AndroidLegacyDatabaseProvider(
    private val context: Context,
    private val driver: SQLiteDriver? = null,
) : LegacyDatabaseProvider {

    private var database: LegacyDatabase? = null

    /**
     * Synchronized because a second caller racing in while the first is still building would get a
     * second handle on the same file and leak the one it displaced.
     */
    @Synchronized
    override fun get(): LegacyDatabase? {
        database?.let { return it }

        val path = context.getDatabasePath(LEGACY_DATABASE_NAME)
        // Room creates any file it is asked to open. Handing it a path that is not there would
        // leave an empty `secure_element_database` behind on an install that never ran v1, and
        // every later run would find that file and treat it as inherited data.
        if (!path.exists()) return null

        return Room.databaseBuilder<LegacyDatabase>(context, LEGACY_DATABASE_NAME)
            .addMigrations(LegacyMigration2To3)
            .apply { driver?.let(::setDriver) }
            .build()
            .also { database = it }
    }

    @Synchronized
    override fun close() {
        // A failure to close must not stop the file from being deleted; a handle we cannot close is
        // exactly the case where leaving the file behind serves the user least.
        runCatching { database?.close() }
        database = null
    }

    override fun delete(): Boolean {
        close()
        return context.deleteDatabase(LEGACY_DATABASE_NAME)
    }
}
