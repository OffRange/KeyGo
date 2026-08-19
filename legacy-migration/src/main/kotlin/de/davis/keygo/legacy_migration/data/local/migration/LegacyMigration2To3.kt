package de.davis.keygo.legacy_migration.data.local.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v1's 2-to-3 step, hand written so the rows that would abort a generated recreate can be dealt
 * with inside the same transaction that recreates the table.
 *
 * Versions 1 and 2 declare `title` and `data` nullable while version 3 declares both NOT NULL, and
 * a generated recreate copies rows straight across with `INSERT ... SELECT`. One row carrying a real
 * NULL therefore aborts the whole migration and costs the user every other row in the file. Room
 * offers no hook that runs before a generated recreate (`AutoMigrationSpec` has only
 * `onPostMigrate`), so the repair has to live in a migration written out by hand.
 *
 * The two columns get opposite treatment, because a NULL in one costs the user everything in that
 * row and a NULL in the other costs them nothing.
 *
 * A NULL `data` is reachable: v1's `Cryptography.encryptAES` swallowed every
 * `GeneralSecurityException` and returned null, and its `@Nullable` `Converters.convertDetails`
 * handed that straight to Room, so a Keystore cipher that failed at save time wrote a NULL blob and
 * the insert succeeded. That is also why the column is nullable in versions 1 and 2 at all. Such a
 * row is **deleted**: there is no ciphertext in it, so there is no secret to recover, and v1 could
 * not read it either, its converter called `decryptAES(null)` and threw an NPE its own catch did
 * not cover. Coalescing it to an empty blob instead would manufacture a row that can never decrypt,
 * and one of those is enough to keep the import reporting a failure forever, which would leave the
 * legacy file and v1's Keystore alias on disk for good.
 *
 * A NULL `title` is the opposite and must **not** be deleted. The blob beside it is intact, so the
 * row is a fully recoverable credential that happens to have no display name. It is coalesced to an
 * empty string and imported; the user gets their password back under a blank name, which is the
 * whole of what was actually missing.
 *
 * Every statement below is copied from v1's own exported `3.json`, which is the schema this has to
 * land on exactly: Room derives its identity hash from the declared entities and refuses a file
 * whose tables do not match. `LegacyMigrationTest` runs this through
 * `MigrationTestHelper.runMigrationsAndValidate`, which is what proves the copy is faithful.
 */
internal object LegacyMigration2To3 : Migration(2, 3) {

    override suspend fun migrate(connection: SQLiteConnection) {
        // Both before the copy, so no row can abort it on a NOT NULL column.
        connection.execSQL("UPDATE `SecureElement` SET `title` = '' WHERE `title` IS NULL")
        connection.execSQL("DELETE FROM `SecureElement` WHERE `data` IS NULL")

        // The recreate itself. Room's own generated form, kept statement for statement.
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `_new_SecureElement` (`title` TEXT NOT NULL, " +
                    "`data` BLOB NOT NULL, `favorite` INTEGER NOT NULL, " +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` INTEGER NOT NULL, " +
                    "`created_at` INTEGER DEFAULT CURRENT_TIMESTAMP, `modified_at` INTEGER)",
        )
        connection.execSQL(
            "INSERT INTO `_new_SecureElement` " +
                    "(`title`,`data`,`favorite`,`id`,`type`,`created_at`,`modified_at`) " +
                    "SELECT `title`,`data`,`favorite`,`id`,`type`,`created_at`,`modified_at` " +
                    "FROM `SecureElement`",
        )
        connection.execSQL("DROP TABLE `SecureElement`")
        connection.execSQL("ALTER TABLE `_new_SecureElement` RENAME TO `SecureElement`")

        // Version 3 has no MasterPassword table. v2's copy holds a bcrypt hash v2 never reads.
        connection.execSQL("DROP TABLE IF EXISTS `MasterPassword`")

        // Tag and the junction are new in version 3. Created after the recreate above, so the
        // foreign keys below point at the table that survives rather than the one that was dropped.
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `Tag` (`name` TEXT NOT NULL, " +
                    "`tagId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)",
        )
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Tag_name` ON `Tag` (`name`)")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `SecureElementTagCrossRef` (`id` INTEGER NOT NULL, " +
                    "`tagId` INTEGER NOT NULL, PRIMARY KEY(`id`, `tagId`), " +
                    "FOREIGN KEY(`id`) REFERENCES `SecureElement`(`id`) " +
                    "ON UPDATE CASCADE ON DELETE CASCADE , " +
                    "FOREIGN KEY(`tagId`) REFERENCES `Tag`(`tagId`) " +
                    "ON UPDATE CASCADE ON DELETE CASCADE )",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_SecureElementTagCrossRef_id` " +
                    "ON `SecureElementTagCrossRef` (`id`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_SecureElementTagCrossRef_tagId` " +
                    "ON `SecureElementTagCrossRef` (`tagId`)",
        )
    }
}
