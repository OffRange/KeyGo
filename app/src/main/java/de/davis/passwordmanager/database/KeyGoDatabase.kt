package de.davis.passwordmanager.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room.databaseBuilder
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import de.davis.passwordmanager.PasswordManagerApplication
import de.davis.passwordmanager.database.converter.Converters
import de.davis.passwordmanager.database.converter.ElementTypeConverter
import de.davis.passwordmanager.database.daos.SecureElementWithTagDao
import de.davis.passwordmanager.database.entities.SecureElementEntity
import de.davis.passwordmanager.database.entities.Tag
import de.davis.passwordmanager.database.entities.junction.SecureElementTagCrossRef
import de.davis.passwordmanager.database.migration.MigrationSpec1To2
import de.davis.passwordmanager.database.migration.MigrationSpec2To3

@TypeConverters(Converters::class, ElementTypeConverter::class)
@Database(
    version = 3,
    entities = [SecureElementEntity::class, Tag::class, SecureElementTagCrossRef::class],
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = MigrationSpec1To2::class),
        AutoMigration(from = 2, to = 3, spec = MigrationSpec2To3::class)
    ]
)
abstract class KeyGoDatabase : RoomDatabase() {

    abstract fun combinedDao(): SecureElementWithTagDao

    companion object {
        private var INSTANCE: KeyGoDatabase? = null

        @JvmStatic
        val instance
            get() = INSTANCE ?: databaseBuilder(
                PasswordManagerApplication.getAppContext(),
                KeyGoDatabase::class.java,
                DB_NAME
            ).fallbackToDestructiveMigration().build()
                .also { INSTANCE = it }
    }
}

const val DB_NAME = "secure_element_database"