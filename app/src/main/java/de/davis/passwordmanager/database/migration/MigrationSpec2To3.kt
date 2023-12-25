package de.davis.passwordmanager.database.migration

import android.annotation.SuppressLint
import androidx.room.DeleteTable
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import de.davis.passwordmanager.database.ElementType

@DeleteTable(tableName = "MasterPassword")
class MigrationSpec2To3 : AutoMigrationSpec {

    @SuppressLint("Range")
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        val cursor = db.query("SELECT id, type FROM SecureElement")
        val tags = mutableMapOf<String, Long>()

        cursor.use {
            while (cursor.moveToNext()) {
                val elementId = cursor.getLong(cursor.getColumnIndex("id"))
                val elementType = cursor.getInt(cursor.getColumnIndex("type"))
                val tag = ElementType.getTypeByTypeId(elementType).tag.name

                // Check if the tag is already inserted and get its ID
                val tagId = tags.getOrPut(tag) { insertTag(db, tag) }

                // Insert into SecureElementTagCrossRef
                insertElementTagCrossRef(db, elementId, tagId)
            }
        }
    }

    @SuppressLint("Range")
    private fun insertTag(db: SupportSQLiteDatabase, tag: String): Long {
        return db.run {
            query("SELECT tagId FROM Tag WHERE name = ?", arrayOf(tag)).use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getLong(cursor.getColumnIndex("tagId"))
                }
            }

            // Insert the tag if it doesn't exist
            compileStatement("INSERT INTO Tag (name) VALUES (?)").use { stmt ->
                stmt.bindString(1, tag)
                stmt.executeInsert()
            }
        }
    }

    private fun insertElementTagCrossRef(db: SupportSQLiteDatabase, elementId: Long, tagId: Long) {
        db.compileStatement("INSERT INTO SecureElementTagCrossRef (id, tagId) VALUES (?, ?)")
            .apply {
                bindLong(1, elementId)
                bindLong(2, tagId)
                executeInsert()
                close()
            }
    }
}