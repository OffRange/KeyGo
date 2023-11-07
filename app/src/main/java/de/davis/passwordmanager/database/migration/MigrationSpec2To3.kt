package de.davis.passwordmanager.database.migration

import androidx.room.DeleteTable
import androidx.room.migration.AutoMigrationSpec

@DeleteTable(tableName = "MasterPassword")
class MigrationSpec2To3 : AutoMigrationSpec