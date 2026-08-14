package de.davis.keygo.legacy_migration

import de.davis.keygo.legacy_migration.data.repository.HashValidatorImpl
import de.davis.keygo.legacy_migration.domain.usecase.ClearMainPasswordUseCase
import de.davis.keygo.legacy_migration.domain.usecase.HasMainPasswordUseCase
import de.davis.keygo.legacy_migration.domain.usecase.ValidateMainPasswordUseCase

fun clearMainPasswordUseCase(repository: FakeMainPasswordRepository): ClearMainPasswordUseCase =
    ClearMainPasswordUseCase(repository.asMainPasswordRepository())

fun hasMainPasswordUseCase(repository: FakeMainPasswordRepository): HasMainPasswordUseCase =
    HasMainPasswordUseCase(repository.asMainPasswordRepository())

fun validateMainPasswordUseCase(repository: FakeMainPasswordRepository): ValidateMainPasswordUseCase =
    ValidateMainPasswordUseCase(HashValidatorImpl(), repository.asMainPasswordRepository())
