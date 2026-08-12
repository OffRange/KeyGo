package de.davis.keygo.migration.create_access

import de.davis.keygo.migration.create_access.data.repository.HashValidatorImpl
import de.davis.keygo.migration.create_access.domain.usecase.ClearMainPasswordUseCase
import de.davis.keygo.migration.create_access.domain.usecase.HasMainPasswordUseCase
import de.davis.keygo.migration.create_access.domain.usecase.ValidateMainPasswordUseCase

fun clearMainPasswordUseCase(repository: FakeMainPasswordRepository): ClearMainPasswordUseCase =
    ClearMainPasswordUseCase(repository.asMainPasswordRepository())

fun hasMainPasswordUseCase(repository: FakeMainPasswordRepository): HasMainPasswordUseCase =
    HasMainPasswordUseCase(repository.asMainPasswordRepository())

fun validateMainPasswordUseCase(repository: FakeMainPasswordRepository): ValidateMainPasswordUseCase =
    ValidateMainPasswordUseCase(HashValidatorImpl(), repository.asMainPasswordRepository())
