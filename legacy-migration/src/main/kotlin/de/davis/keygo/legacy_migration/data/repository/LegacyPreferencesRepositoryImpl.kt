package de.davis.keygo.legacy_migration.data.repository

import android.content.Context
import de.davis.keygo.legacy_migration.domain.repository.LegacyPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

internal const val LEGACY_PREFERENCES_SUFFIX = "_preferences"

internal fun legacyPreferencesName(packageName: String) = packageName + LEGACY_PREFERENCES_SUFFIX

@Single
internal class LegacyPreferencesRepositoryImpl(
    private val context: Context,
) : LegacyPreferencesRepository {

    override suspend fun deleteLegacyPreferences() {
        withContext(Dispatchers.IO) {
            runCatching {
                context.deleteSharedPreferences(legacyPreferencesName(context.packageName))
            }
        }
    }
}
