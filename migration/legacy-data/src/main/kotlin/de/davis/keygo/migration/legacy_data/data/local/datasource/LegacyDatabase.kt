package de.davis.keygo.migration.legacy_data.data.local.datasource

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import de.davis.keygo.migration.legacy_data.data.local.dao.LegacyElementDao
import de.davis.keygo.migration.legacy_data.data.local.entity.LegacySecureElementEntity
import de.davis.keygo.migration.legacy_data.data.local.entity.LegacySecureElementTagCrossRef
import de.davis.keygo.migration.legacy_data.data.local.entity.LegacyTagEntity
import de.davis.keygo.migration.legacy_data.data.local.migration.LegacyMigrationSpec1To2
import de.davis.keygo.migration.legacy_data.data.local.migration.LegacyMigrationSpec2To3

internal const val LEGACY_DATABASE_NAME = "secure_element_database"

/**
 * KeyGo v1's database, ported entity for entity so the inherited file can be read through Room.
 *
 * Version 3 is final; v1 will never ship again. Versions 1 and 2 are still reachable because a user
 * can update to v2 directly from an early v1 build whose file never auto-migrated. The two
 * auto-migrations are generated from v1's own exported `1.json` and `2.json`, which is why 2-to-3
 * needs no hand-written SQL despite being a table recreate.
 *
 * Opening this over an inherited file is a one-way door, but only once the open succeeds. The first
 * query runs the auto-migrations, which permanently rewrite the file to version 3 and drop its
 * `MasterPassword` table. An import that gets past that point and then fails is retried against a
 * file already at version 3, never at the version it started from.
 *
 * A migration that fails is the other case and behaves the opposite way. Room runs each one in a
 * transaction, so an abort rolls the file back to the version it came in at, measured as 2 in
 * `LegacyDatabaseOpenTest`. That is what makes `LegacyDatabaseSanitizer`'s "below version 3 only"
 * guard useful on a retry: a file whose recreate aborted still reads as version 2, so the sanitizer
 * still sees it and can repair it.
 */
@Database(
    version = 3,
    entities = [
        LegacySecureElementEntity::class,
        LegacyTagEntity::class,
        LegacySecureElementTagCrossRef::class,
    ],
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = LegacyMigrationSpec1To2::class),
        AutoMigration(from = 2, to = 3, spec = LegacyMigrationSpec2To3::class),
    ],
    exportSchema = true,
)
internal abstract class LegacyDatabase : RoomDatabase() {

    abstract fun legacyElementDao(): LegacyElementDao
}

/**
 * Opens the legacy database on demand.
 *
 * Indirection rather than an injected instance for two reasons: on a clean v2 install the file
 * never exists and eagerly opening it would create an empty one, and the repository re-opens after
 * `deleteDatabase` closes the previous handle.
 */
internal fun interface LegacyDatabaseProvider {

    fun get(): LegacyDatabase
}
