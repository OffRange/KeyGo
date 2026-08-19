package de.davis.keygo.legacy_migration.domain.usecase

import de.davis.keygo.legacy_migration.domain.model.LegacyMigrationOutcome

/**
 * The one thing [RunPendingMigrationUseCase] needs of the v1 item import: run it, and say how it
 * ended.
 *
 * Narrow on purpose. The sequencing this module exists to guarantee is worth testing on its own,
 * and standing up Room, a SQLite driver, the Keystore and the whole v2 key hierarchy behind every
 * one of those tests would say nothing extra about the sequencing.
 */
internal fun interface LegacyDataImporter {

    suspend operator fun invoke(): LegacyMigrationOutcome
}
