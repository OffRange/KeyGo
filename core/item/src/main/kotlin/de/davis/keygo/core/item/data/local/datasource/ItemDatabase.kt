package de.davis.keygo.core.item.data.local.datasource

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import de.davis.keygo.core.item.data.local.dao.DomainInfoDao
import de.davis.keygo.core.item.data.local.dao.ItemDao
import de.davis.keygo.core.item.data.local.dao.LoginDao
import de.davis.keygo.core.item.data.local.dao.PasskeyDao
import de.davis.keygo.core.item.data.local.dao.PasswordDao
import de.davis.keygo.core.item.data.local.dao.TotpDao
import de.davis.keygo.core.item.data.local.dao.VaultDao
import de.davis.keygo.core.item.data.local.entity.DomainInfoEntity
import de.davis.keygo.core.item.data.local.entity.ItemEntity
import de.davis.keygo.core.item.data.local.entity.LoginEntity
import de.davis.keygo.core.item.data.local.entity.VaultEntity
import de.davis.keygo.core.item.data.local.entity.credential.PasskeyEntity
import de.davis.keygo.core.item.data.local.entity.credential.PasswordEntity
import de.davis.keygo.core.item.data.local.entity.credential.TotpEntity
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Database(
    entities = [
        VaultEntity::class,
        ItemEntity::class,
        LoginEntity::class,
        TotpEntity::class,
        PasswordEntity::class,
        DomainInfoEntity::class,
        PasskeyEntity::class,
    ],
    version = 1,
)
internal abstract class ItemDatabase : RoomDatabase() {

    abstract fun vaultDao(): VaultDao

    abstract fun itemDao(): ItemDao

    abstract fun loginDao(): LoginDao

    abstract fun totpDao(): TotpDao

    abstract fun passwordDao(): PasswordDao

    abstract fun domainInfoDao(): DomainInfoDao

    abstract fun passkeyDao(): PasskeyDao
}

@Module
internal class DatabaseModule {

    @Single
    fun provideDatabase(context: Context): ItemDatabase =
        Room.databaseBuilder(
            context,
            ItemDatabase::class.java,
            "secure_element_database",
        ).build()

    @Single
    fun provideVaultDao(db: ItemDatabase) = db.vaultDao()

    @Single
    fun provideItemDao(db: ItemDatabase) = db.itemDao()

    @Single
    fun provideLoginDao(db: ItemDatabase) = db.loginDao()

    @Single
    fun provideTotpDao(db: ItemDatabase) = db.totpDao()

    @Single
    fun providePasswordDao(db: ItemDatabase) = db.passwordDao()

    @Single
    fun provideDomainInfoDao(db: ItemDatabase) = db.domainInfoDao()

    @Single
    fun providePasskeyDao(db: ItemDatabase) = db.passkeyDao()
}
