package de.davis.keygo.migration.legacy_data.di

import android.content.Context
import de.davis.keygo.migration.legacy_data.data.local.datasource.AndroidLegacySecureElementProbe
import de.davis.keygo.migration.legacy_data.data.local.datasource.LEGACY_DATABASE_NAME
import de.davis.keygo.migration.legacy_data.data.local.datasource.LegacyDatabaseProvider
import de.davis.keygo.migration.legacy_data.data.local.datasource.LegacySecureElementProbe
import de.davis.keygo.migration.legacy_data.data.local.datasource.SanitizingLegacyDatabaseProvider
import de.davis.keygo.migration.legacy_data.domain.repository.LegacyDatabaseFiles
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
internal class LegacyDatabaseModule {

    @Single
    fun provideLegacyDatabaseProvider(context: Context): LegacyDatabaseProvider =
        SanitizingLegacyDatabaseProvider(context)

    @Single
    fun provideLegacySecureElementProbe(context: Context): LegacySecureElementProbe =
        AndroidLegacySecureElementProbe(context)

    @Single
    fun provideLegacyDatabaseFiles(context: Context): LegacyDatabaseFiles =
        AndroidLegacyDatabaseFiles(context)
}

private class AndroidLegacyDatabaseFiles(
    private val context: Context,
) : LegacyDatabaseFiles {

    override fun exists(): Boolean = context.getDatabasePath(LEGACY_DATABASE_NAME).exists()

    override fun delete(): Boolean = context.deleteDatabase(LEGACY_DATABASE_NAME)
}
