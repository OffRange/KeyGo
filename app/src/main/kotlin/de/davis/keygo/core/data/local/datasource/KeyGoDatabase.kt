package de.davis.keygo.core.data.local.datasource

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import de.davis.keygo.core.data.converter.crypto.CryptoConverter
import de.davis.keygo.core.data.local.dao.PasswordDao
import de.davis.keygo.core.data.local.dao.VaultDao
import de.davis.keygo.core.data.local.model.Password
import de.davis.keygo.core.data.local.model.VaultItem
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

@Database(entities = [VaultItem::class, Password::class], version = 1)
@TypeConverters(CryptoConverter::class)
abstract class KeyGoDatabase : RoomDatabase() {

    abstract fun vaultDao(): VaultDao
    abstract fun passwordDao(): PasswordDao

    companion object {
        internal val koinModule = module {
            single { create(get()) }

            singleOf(KeyGoDatabase::vaultDao)
            singleOf(KeyGoDatabase::passwordDao)
        }

        private fun create(applicationContext: Context) = Room.databaseBuilder(
            applicationContext,
            KeyGoDatabase::class.java,
            name = "secure_element_database"
        ).fallbackToDestructiveMigration(false).build()
    }
}