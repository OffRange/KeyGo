package de.davis.keygo.migration.legacy_data.data.local.datasource

import android.content.Context
import androidx.room.Room

/**
 * Repairs the inherited file, then opens it.
 *
 * The order is the whole point. Room runs the 2-to-3 recreate on the first query, and a single row
 * carrying a NULL `title` or `data` aborts it and takes every other row in the file down with it.
 * The sanitizer is the only thing standing between one bad row and the user losing all of them, and
 * it can only do its work while the file is still closed, so it runs here rather than anywhere
 * further in. It is cheap and self-guarding on a file that needs nothing, so every open pays for it.
 */
internal class SanitizingLegacyDatabaseProvider(
    private val context: Context,
    private val sanitizer: LegacyDatabaseSanitizer = LegacyDatabaseSanitizer(),
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

        val repaired = try {
            sanitizer.sanitize(context.getDatabasePath(LEGACY_DATABASE_NAME).absolutePath)
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
