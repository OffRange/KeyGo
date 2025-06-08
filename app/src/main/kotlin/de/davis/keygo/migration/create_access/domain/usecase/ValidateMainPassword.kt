package de.davis.keygo.migration.create_access.domain.usecase

import de.davis.keygo.core.domain.Result
import de.davis.keygo.core.domain.asResult
import de.davis.keygo.core.domain.error.ValidationError
import de.davis.keygo.core.domain.model.crypto.asCryptographicData
import de.davis.keygo.migration.create_access.domain.repository.HashValidator
import de.davis.keygo.migration.create_access.domain.repository.MainPasswordRepository
import org.koin.core.annotation.Single

@Single
class ValidateMainPassword(
    private val hashValidator: HashValidator,
    private val mainPasswordRepository: MainPasswordRepository
) {

    suspend operator fun invoke(password: String): Result<Unit, ValidationError> =
        mainPasswordRepository.getMainPassword().hash.let {
            hashValidator.validateHash(
                plainText = password.toByteArray(),
                hash = it.asCryptographicData().data
            ).asResult(ValidationError.NoMatch)
        }
}