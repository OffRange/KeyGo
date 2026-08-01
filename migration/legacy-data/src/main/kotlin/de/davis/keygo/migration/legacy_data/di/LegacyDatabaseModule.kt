package de.davis.keygo.migration.legacy_data.di

import android.content.Context
import de.davis.keygo.migration.legacy_data.data.local.datasource.AndroidLegacyDatabaseProvider
import de.davis.keygo.migration.legacy_data.data.local.datasource.LegacyDatabaseProvider
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
internal class LegacyDatabaseModule {

    @Single
    fun provideLegacyDatabaseProvider(context: Context): LegacyDatabaseProvider =
        AndroidLegacyDatabaseProvider(context)
}
