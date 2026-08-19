package de.davis.keygo.legacy_migration.di

import android.content.Context
import de.davis.keygo.legacy_migration.data.local.datasource.AndroidLegacyDatabaseProvider
import de.davis.keygo.legacy_migration.data.local.datasource.LegacyDatabaseProvider
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
internal class LegacyDatabaseModule {

    @Single
    fun provideLegacyDatabaseProvider(context: Context): LegacyDatabaseProvider =
        AndroidLegacyDatabaseProvider(context)
}
