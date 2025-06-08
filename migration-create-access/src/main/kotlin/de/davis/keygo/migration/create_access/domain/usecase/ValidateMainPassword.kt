package de.davis.keygo.migration.create_access.domain.usecase

import de.davis.keygo.migration.create_access.domain.repository.HashValidator
import de.davis.keygo.migration.create_access.domain.repository.MainPasswordRepository
import org.koin.core.annotation.Single

@Single
class ValidateMainPassword internal constructor(
    private val hashValidator: HashValidator,
    private val mainPasswordRepository: MainPasswordRepository
) {

    @OptIn(ExperimentalStdlibApi::class)
    suspend operator fun invoke(password: String): Boolean =
        mainPasswordRepository.getMainPassword().hash.let {
            hashValidator.validateHash(
                plainText = password.toByteArray(),
                hash = it.hexToByteArray()
            )
        }
}