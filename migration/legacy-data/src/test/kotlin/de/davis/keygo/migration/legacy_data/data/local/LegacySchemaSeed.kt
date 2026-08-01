package de.davis.keygo.migration.legacy_data.data.local

import android.app.Instrumentation
import android.content.Context
import android.content.res.AssetManager
import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.davis.keygo.migration.legacy_data.data.local.datasource.LegacyDatabase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Where v1's exported schema JSON lives, handed over by the `Test` task in `build.gradle.kts`.
 *
 * A system property rather than a relative path, because a relative path only resolves while the
 * working directory happens to be the module directory, which is true under Gradle and not
 * guaranteed in an IDE run configuration.
 */
internal val LEGACY_SCHEMA_DIR: Path = Paths.get(
    checkNotNull(System.getProperty("legacySchemaDir")) {
        "legacySchemaDir is unset; the Test task in build.gradle.kts is what supplies it"
    },
).resolve(LegacyDatabase::class.qualifiedName)

private fun schemaInstrumentation(): Instrumentation {
    val assets = mockk<AssetManager>()
    every { assets.open(any()) } answers {
        LEGACY_SCHEMA_DIR.parent.resolve(firstArg<String>()).toFile().inputStream()
    }

    val context = object : LegacyStubContext() {
        override fun getAssets(): AssetManager = assets
    }

    return object : Instrumentation() {
        override fun getContext(): Context = context

        override fun getTargetContext(): Context = context
    }
}

/**
 * Writes a file at [file] that looks exactly like one a v1 build at [version] would have left
 * behind, and hands back the open connection so the caller can seed rows into it.
 *
 * Room's own `MigrationTestHelper` does the writing, from v1's exported schema rather than from the
 * ported entities under test, which is what keeps the proof honest. Versions 1 and 2 in that
 * directory *are* v1's own files, copied in verbatim so Room can generate the 1-to-2 migration from
 * them, which is why there is one copy of them rather than two.
 *
 * Only the helper's `createDatabase` is used. Its `runMigrationsAndValidate` is deliberately left
 * alone, because it takes the migration list from the caller: the tests instead open the seeded file
 * through [de.davis.keygo.migration.legacy_data.data.local.datasource.AndroidLegacyDatabaseProvider],
 * which validates the post-migration schema through real Room *and* fails if production ever stops
 * registering the hand-written 2-to-3. See [LegacyMigrationTest].
 *
 * Not suspending, though `createDatabase` is, so that the handful of callers which have no reason to
 * be coroutines do not have to become them.
 */
internal fun seedLegacyDatabase(file: File, version: Int): SQLiteConnection = runBlocking {
    MigrationTestHelper(
        instrumentation = schemaInstrumentation(),
        file = file,
        driver = BundledSQLiteDriver(),
        databaseClass = LegacyDatabase::class,
    ).createDatabase(version)
}

/** The version stamp read straight off [file], which is how a test sees whether Room ran. */
internal fun userVersionOf(file: File): Int =
    BundledSQLiteDriver().open(file.absolutePath).use { connection ->
        connection.prepare("PRAGMA user_version").use { stmt ->
            stmt.step()
            stmt.getLong(0).toInt()
        }
    }
