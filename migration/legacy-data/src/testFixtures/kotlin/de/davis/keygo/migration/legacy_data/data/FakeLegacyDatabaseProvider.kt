package de.davis.keygo.migration.legacy_data.data

import de.davis.keygo.migration.legacy_data.data.local.datasource.LegacyDatabase
import de.davis.keygo.migration.legacy_data.data.local.datasource.LegacyDatabaseProvider
import java.io.File

/**
 * Hands out one already-open database, and nothing once the file behind it has gone, which is the
 * rule production's provider enforces by checking the path before it builds anything.
 */
internal class FakeLegacyDatabaseProvider(
    private val file: File,
    private val database: LegacyDatabase?,
) : LegacyDatabaseProvider {

    var closed: Boolean = false
        private set

    private var fileDeleted: Boolean = false

    override fun get(): LegacyDatabase? = database.takeUnless { fileDeleted }

    override fun close() {
        closed = true
        runCatching { database?.close() }
    }

    /**
     * Answers whether a file actually went, the way `Context.deleteDatabase` does. A flat `true`
     * would report a deletion on a clean install that had nothing to delete, and v1's Keystore
     * alias is gated on this answer.
     */
    override fun delete(): Boolean {
        close()
        fileDeleted = true
        return SUFFIXES.count { File(file.absolutePath + it).delete() } > 0
    }

    private companion object {
        val SUFFIXES = listOf("", "-wal", "-shm", "-journal")
    }
}
