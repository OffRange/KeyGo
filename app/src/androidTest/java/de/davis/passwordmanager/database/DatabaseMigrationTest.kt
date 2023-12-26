package de.davis.passwordmanager.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room.databaseBuilder
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.davis.passwordmanager.database.converter.Converters
import de.davis.passwordmanager.database.entities.details.password.PasswordDetails
import de.davis.passwordmanager.database.entities.wrappers.CombinedElement
import de.davis.passwordmanager.database.migration.MigrationSpec1To2
import de.davis.passwordmanager.database.migration.MigrationSpec2To3
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException


private const val TITLE = "passwordElement"
private const val PASSWORD = "password"
private const val ORIGIN = "origin"
private const val USERNAME = "username"
private const val TEST_DB = "migration-database"

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        KeyGoDatabase::class.java,
        listOf(
            MigrationSpec1To2(),
            MigrationSpec2To3()
        )
    )

    @Test
    @Throws(IOException::class)
    fun testAllMigrations() = runTest {
        helper.createDatabase(TEST_DB, 1).apply {
            val contentValues = ContentValues().apply {
                put("title", TITLE)
                put("type", ElementType.PASSWORD.typeId)
                put(
                    "data",
                    Converters.convertDetails(
                        PasswordDetails(
                            PASSWORD,
                            ORIGIN,
                            USERNAME
                        )
                    )
                )
            }
            insert("SecureElement", SQLiteDatabase.CONFLICT_REPLACE, contentValues)
            close()
        }
        databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            KeyGoDatabase::class.java,
            TEST_DB
        ).build().apply {
            openHelper.writableDatabase
            val element: CombinedElement = combinedDao().getCombinedElement().first()
            element.secureElementEntity.run {
                assertEquals(TITLE, title)
                assertFalse(favorite)
                assertNull(timestamps.modifiedAt)
                assertNotNull(timestamps.createdAt)
                (detail as PasswordDetails).run {
                    assertEquals(PASSWORD, password)
                    assertEquals(ORIGIN, origin)
                    assertEquals(USERNAME, username)
                }
            }
            close()
        }
    }
}