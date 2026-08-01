package de.davis.keygo.migration.legacy_data.data.local

import android.content.Context
import android.content.ContextWrapper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.davis.keygo.migration.legacy_data.data.local.datasource.AndroidLegacyDatabaseProvider
import de.davis.keygo.migration.legacy_data.data.local.datasource.LegacyDatabase
import java.io.File
import kotlin.test.assertNotNull

/**
 * A [Context] that answers only what Room reaches for, and throws for everything else.
 *
 * A relaxed mock would answer *every* call, which is the opposite of what is wanted here: an
 * unstubbed answer is how a test ends up quietly reading some other file and reporting an empty
 * database. The two overrides are not guesses, they are what Room's android builder actually reaches
 * for on the way to opening a file:
 * - `getSystemService` returns null. Room asks for `ActivityManager` to decide whether it is on a
 *   low RAM device, and treats an absent service the same as a device that is not.
 * - `getApplicationContext` returns this, because a null base context has none to delegate to.
 *
 * A `ContextWrapper` with a null base rather than a subclass of `Context`, because `Context` is
 * abstract across dozens of members and none of the others are ever called.
 */
internal open class LegacyStubContext : ContextWrapper(null) {

    override fun getSystemService(name: String): Any? = null

    override fun getApplicationContext(): Context = this
}

/**
 * Answers `getDatabasePath` with [databaseFile]. Both production classes in this module that take a
 * Context use it only to turn `secure_element_database` into a path, so pointing that one call at a
 * temp file is the whole of what a test needs.
 */
internal fun legacyContext(databaseFile: File): Context = object : LegacyStubContext() {

    override fun getDatabasePath(name: String?): File = databaseFile
}

/**
 * Opened through the production provider rather than a bare `Room.databaseBuilder`, so a test cannot
 * pass while the provider forgets to register the hand-written 2-to-3 migration.
 */
internal fun openMigratedLegacyDatabase(databaseFile: File): LegacyDatabase =
    assertNotNull(
        AndroidLegacyDatabaseProvider(legacyContext(databaseFile), BundledSQLiteDriver()).get(),
    )
