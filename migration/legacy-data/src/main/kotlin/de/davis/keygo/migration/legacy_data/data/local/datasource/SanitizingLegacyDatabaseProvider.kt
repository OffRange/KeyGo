package de.davis.keygo.migration.legacy_data.data.local.datasource

import android.content.Context
import androidx.room.Room
import androidx.sqlite.SQLiteDriver

/**
 * Repairs the inherited file, then opens it. Never creates one.
 *
 * The order is the whole point. Room runs the 2-to-3 recreate on the first query, and a single row
 * carrying a NULL `title` or `data` aborts it and takes every other row in the file down with it.
 * The sanitizer is the only thing standing between one bad row and the user losing all of them, and
 * it can only do its work while the file is still closed, so it runs here rather than anywhere
 * further in. It is cheap and self-guarding on a file that needs nothing, so every open pays for it.
 *
 * Both of this class's guards are about the file's state before Room is allowed to touch it, which
 * is why they sit together: one repairs what is there, the other refuses to invent what is not.
 */
/**
 * @param driver left null in production so Room keeps the framework helper it has always used here.
 * A JVM test has no framework helper to use, so it passes a bundled driver; without one, Room
 * cannot create a database file at all and a test asserting that no file appears could not fail.
 */
internal class SanitizingLegacyDatabaseProvider(
    private val context: Context,
    private val sanitizer: LegacyDatabaseSanitizer = LegacyDatabaseSanitizer(),
    private val driver: SQLiteDriver? = null,
) : LegacyDatabaseProvider {

    private var database: LegacyDatabase? = null

    override var repairedRows: Int = 0
        private set

    /**
     * Synchronized because a second caller racing in while the first is still building would get a
     * second handle on the same file and leak the one it displaced.
     */
    @Synchronized
    override fun get(): LegacyDatabase? {
        database?.let { return it }

        val path = context.getDatabasePath(LEGACY_DATABASE_NAME)
        // This module only ever reads a database it inherited, and Room creates any file it is
        // asked to open. Handing it a path that is not there would leave an empty
        // `secure_element_database` behind on an install that never ran v1, and every later run
        // would find that file and treat it as inherited data. There is nothing to read here, so
        // there is nothing to open. The sanitizer no-ops on a missing file rather than reporting
        // one, so this is the guard that has to stop the builder.
        if (!path.exists()) return null

        val repaired = try {
            sanitizer.sanitize(path.absolutePath)
        } catch (_: Exception) {
            // The sanitizer has no failure channel of its own: its guards cover a missing file, an
            // already migrated one and a non-v1 one, and everything else it meets it opens. A
            // corrupt, truncated or foreign file therefore throws out of the driver, the PRAGMA or
            // the sqlite_master probe. Nothing can be read out of such a file, so it becomes an
            // answer here instead of an exception thrown across the unlock flow.
            return null
        }

        repairedRows += repaired
        return Room.databaseBuilder(context, LegacyDatabase::class.java, LEGACY_DATABASE_NAME)
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
}
