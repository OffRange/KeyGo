package de.davis.passwordmanager.database.migration

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

class MigrationSpec1To2 : AutoMigrationSpec {

    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        val cv = ContentValues().apply {
            put("created_at", System.currentTimeMillis())
        }
        db.update(
            "SecureElement",
            SQLiteDatabase.CONFLICT_REPLACE,
            cv,
            "created_at is ?",
            arrayOf(null)
        )
    }
}