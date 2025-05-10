package de.davis.keygo.core.data.local.datasource

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import de.davis.keygo.core.data.converter.crypto.CryptoConverter
import de.davis.keygo.core.data.local.dao.PasswordDao
import de.davis.keygo.core.data.local.dao.VaultDao
import de.davis.keygo.generated.item.data.local.entity.PasswordEntity
import de.davis.keygo.generated.item.data.local.entity.VaultItemEntity
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

@Database(entities = [VaultItemEntity::class, PasswordEntity::class], version = 1)
@TypeConverters(CryptoConverter::class)
abstract class KeyGoDatabase : RoomDatabase() {

    internal abstract fun vaultDao(): VaultDao
    internal abstract fun passwordDao(): PasswordDao

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