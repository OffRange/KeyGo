package de.davis.keygo.migration.create_access.domain.usecase

import de.davis.keygo.migration.create_access.domain.repository.MainPasswordRepository
import org.koin.core.annotation.Single

@Single
class HasMainPasswordUseCase(
    private val mainPasswordRepository: MainPasswordRepository
) {

    suspend operator fun invoke(): Boolean =
        mainPasswordRepository.getMainPassword().hash.isNotEmpty()
}