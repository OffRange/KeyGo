package de.davis.keygo.legacy_migration.domain.repository

/**
 * v1's settings file. Nothing in v2 reads it, so the only thing left to do with it is take it away.
 */
internal interface LegacyPreferencesRepository {

    /**
     * Removes v1's preferences file, and answers nothing back.
     *
     * Deliberately not a `Result`, and deliberately unable to fail the run. The caller has no
     * answer to a settings file that will not go: it holds no secret and gates nothing, and letting
     * it fail the run would hold the migration open and reimport the user's vault on every unlock.
     */
    suspend fun deleteLegacyPreferences()
}
