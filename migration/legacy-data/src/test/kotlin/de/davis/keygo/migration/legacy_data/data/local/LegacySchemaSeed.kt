package de.davis.keygo.migration.legacy_data.data.local

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

private val json = Json { ignoreUnknownKeys = true }

/**
 * Writes a file at [file] that looks exactly like one a v1 build at [version] would have left
 * behind, and hands back the open connection so the caller can seed rows into it.
 *
 * Built by replaying v1's own exported schema JSON rather than with Room's `MigrationTestHelper`,
 * whose Android artifact takes an `android.app.Instrumentation` in every constructor and so cannot
 * run on a plain JVM test. Replaying `createSql` and `setupQueries` is what the helper does anyway,
 * and it keeps the seed honest: it comes from v1's export, not from the ported entities under test.
 *
 * One copy for the whole test source set. The shape of that JSON is a detail every seeding test
 * would otherwise have to know, and three copies of it are three places to update when one of them
 * is wrong.
 */
internal fun seedLegacyDatabase(file: File, version: Int): SQLiteConnection {
    val schema = json
        .parseToJsonElement(File("src/test/resources/legacy-schemas/$version.json").readText())
        .jsonObject
        .getValue("database")
        .jsonObject

    return BundledSQLiteDriver().open(file.absolutePath).apply {
        schema.getValue("entities").jsonArray.forEach { entity ->
            val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
            execSQL(entity.jsonObject.createSqlFor(table))
            entity.jsonObject.getValue("indices").jsonArray.forEach { index ->
                execSQL(index.jsonObject.createSqlFor(table))
            }
        }
        schema.getValue("setupQueries").jsonArray.forEach { execSQL(it.jsonPrimitive.content) }
        execSQL("PRAGMA user_version = $version")
    }
}

private fun JsonObject.createSqlFor(tableName: String): String =
    getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", tableName)

/** The version stamp read straight off [file], which is how a test sees whether Room ran. */
internal fun userVersionOf(file: File): Int =
    BundledSQLiteDriver().open(file.absolutePath).use { connection ->
        connection.prepare("PRAGMA user_version").use { stmt ->
            stmt.step()
            stmt.getLong(0).toInt()
        }
    }
