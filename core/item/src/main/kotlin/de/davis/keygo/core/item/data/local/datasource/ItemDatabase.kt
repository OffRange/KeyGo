package de.davis.keygo.core.item.data.local.datasource

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import de.davis.keygo.core.item.data.local.converter.SecretDataConverter
import de.davis.keygo.core.item.data.local.dao.DomainInfoDao
import de.davis.keygo.core.item.data.local.dao.PasskeyDao
import de.davis.keygo.core.item.data.local.dao.PasswordDao
import de.davis.keygo.core.item.data.local.dao.VaultDao
import de.davis.keygo.core.item.data.local.entity.DomainInfoEntity
import de.davis.keygo.core.item.data.local.entity.PasskeyEntity
import de.davis.keygo.core.item.data.local.entity.PasswordEntity
import de.davis.keygo.core.item.data.local.entity.VaultItemEntity
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

@Database(
    entities = [
        VaultItemEntity::class,
        PasswordEntity::class,
        DomainInfoEntity::class,
        PasskeyEntity::class
    ],
    version = 1
)
@TypeConverters(SecretDataConverter::class)
abstract class ItemDatabase : RoomDatabase() {

    internal abstract fun vaultDao(): VaultDao
    internal abstract fun passwordDao(): PasswordDao
    internal abstract fun domainInfoDao(): DomainInfoDao
    internal abstract fun passkeyDao(): PasskeyDao

    companion object {
        val koinModule = module {
            single { create(get()) }

            singleOf(ItemDatabase::vaultDao)
            singleOf(ItemDatabase::passwordDao)
            singleOf(ItemDatabase::domainInfoDao)
            singleOf(ItemDatabase::passkeyDao)
        }

        private fun create(applicationContext: Context) = Room.databaseBuilder(
            applicationContext,
            ItemDatabase::class.java,
            name = "secure_element_database"
        ).fallbackToDestructiveMigration(false).build()
    }
}