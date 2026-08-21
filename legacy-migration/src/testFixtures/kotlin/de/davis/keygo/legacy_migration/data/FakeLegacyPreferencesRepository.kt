package de.davis.keygo.legacy_migration.data

import de.davis.keygo.legacy_migration.domain.repository.LegacyPreferencesRepository

/**
 * Records that v1's settings file was asked to go. There is nothing to answer back with: the real
 * repository reports no outcome either, on purpose.
 */
internal class FakeLegacyPreferencesRepository : LegacyPreferencesRepository {

    var deleted: Boolean = false
        private set

    override suspend fun deleteLegacyPreferences() {
        deleted = true
    }
}
