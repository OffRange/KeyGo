package de.davis.keygo.core.data.local.datasource

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import org.koin.dsl.module

abstract class KeyGoDatabase : RoomDatabase() {


    companion object {
        internal val koinModule = module {
            single { create(get()) }
            //singleOf(CashCounterDatabase::getCurrency)
            //singleOf(CashCounterDatabase::getWallet)
            //singleOf(CashCounterDatabase::getDenomination)
        }

        private fun create(applicationContext: Context) = Room.databaseBuilder(
            applicationContext,
            KeyGoDatabase::class.java,
            name = "secure_element_database"
        ).fallbackToDestructiveMigration().build()
    }
}