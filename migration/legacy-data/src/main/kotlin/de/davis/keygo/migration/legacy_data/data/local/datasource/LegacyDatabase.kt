package de.davis.keygo.migration.legacy_data.data.local.datasource

import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.RoomDatabase
import de.davis.keygo.migration.legacy_data.data.local.dao.LegacyElementDao
import de.davis.keygo.migration.legacy_data.data.local.entity.LegacySecureElementEntity
import de.davis.keygo.migration.legacy_data.data.local.entity.LegacySecureElementTagCrossRef
import de.davis.keygo.migration.legacy_data.data.local.entity.LegacyTagEntity

internal const val LEGACY_DATABASE_NAME = "secure_element_database"

/**
 * KeyGo v1's database, ported entity for entity so the inherited file can be read through Room.
 *
 * Version 3 is final; v1 will never ship again. Versions 1 and 2 are still reachable because a user
 * can update to v2 directly from an early v1 build whose file never auto-migrated.
 *
 * 1-to-2 only adds columns, so Room generates it from v1's own exported `1.json`. 2-to-3 is a table
 * recreate that a row with a NULL `title` or `data` would abort, so it is hand written instead and
 * registered on the builder rather than here: see
 * [de.davis.keygo.migration.legacy_data.data.local.migration.LegacyMigration2To3].
 *
 * Opening this over an inherited file is a one-way door, but only once the open succeeds. The first
 * query runs the migrations, which permanently rewrite the file to version 3 and drop its
 * `MasterPassword` table. An import that gets past that point and then fails is retried against a
 * file already at version 3, never at the version it started from.
 *
 * A migration that fails is the other case and behaves the opposite way. Room runs each one in a
 * transaction, so an abort rolls the file back to the version it came in at, which is what lets a
 * retry start from exactly where the last attempt did.
 */
@Database(
    version = 3,
    entities = [
        LegacySecureElementEntity::class,
        LegacyTagEntity::class,
        LegacySecureElementTagCrossRef::class,
    ],
    autoMigrations = [AutoMigration(from = 1, to = 2)],
    exportSchema = true,
)
internal abstract class LegacyDatabase : RoomDatabase() {

    abstract fun legacyElementDao(): LegacyElementDao
}
