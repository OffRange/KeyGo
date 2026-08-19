package de.davis.keygo.legacy_migration

import de.davis.keygo.legacy_migration.data.repository.HashValidatorImpl
import de.davis.keygo.legacy_migration.domain.model.LegacyMigrationOutcome
import de.davis.keygo.legacy_migration.domain.usecase.ClearMainPasswordUseCase
import de.davis.keygo.legacy_migration.domain.usecase.HasMainPasswordUseCase
import de.davis.keygo.legacy_migration.domain.usecase.LegacyDataImporter
import de.davis.keygo.legacy_migration.domain.usecase.RunPendingMigrationUseCase
import de.davis.keygo.legacy_migration.domain.usecase.ValidateMainPasswordUseCase
import kotlinx.coroutines.CoroutineScope

internal fun clearMainPasswordUseCase(repository: FakeMainPasswordRepository): ClearMainPasswordUseCase =
    ClearMainPasswordUseCase(repository.asMainPasswordRepository())

fun hasMainPasswordUseCase(repository: FakeMainPasswordRepository): HasMainPasswordUseCase =
    HasMainPasswordUseCase(repository.asMainPasswordRepository())

fun validateMainPasswordUseCase(repository: FakeMainPasswordRepository): ValidateMainPasswordUseCase =
    ValidateMainPasswordUseCase(HashValidatorImpl(), repository.asMainPasswordRepository())

/**
 * Builds the real sequencing over a fake marker and a canned import outcome, so a consumer outside
 * this module can drive the auth screen through a finished, a partial and a failed migration
 * without depending on anything internal to it. [onImport] fires once per run, for callers that
 * need to see that a retry actually retried.
 */
fun runPendingMigrationUseCase(
    scope: CoroutineScope,
    repository: FakeMainPasswordRepository,
    outcome: LegacyMigrationOutcome = LegacyMigrationOutcome.NothingToMigrate,
    onImport: () -> Unit = {},
): RunPendingMigrationUseCase = RunPendingMigrationUseCase(
    hasMainPassword = hasMainPasswordUseCase(repository),
    importLegacyData = LegacyDataImporter {
        onImport()
        outcome
    },
    clearMainPassword = clearMainPasswordUseCase(repository),
    scope = scope,
)
