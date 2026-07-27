package de.davis.keygo.migration.legacy_data.data.local.migration

import androidx.room.DeleteTable
import androidx.room.migration.AutoMigrationSpec

/**
 * v1's 1-to-2 spec backfilled `created_at` where it was null. The importer does not need that:
 * `LegacyElementMapper` already falls back to the current time for a null `created_at`, which it
 * has to do anyway for rows that predate the column. Keeping this a no-op keeps hand-written SQL
 * out of the module.
 */
internal class LegacyMigrationSpec1To2 : AutoMigrationSpec

/**
 * v1's 2-to-3 spec inserted `elementType:` discriminator tags, which the importer discards, so the
 * body is dropped. The annotation is not optional: version 2 has a `MasterPassword` table that
 * version 3 does not, and Room needs to be told the table was deleted rather than renamed.
 */
@DeleteTable(tableName = "MasterPassword")
internal class LegacyMigrationSpec2To3 : AutoMigrationSpec
