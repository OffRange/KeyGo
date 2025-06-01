package de.davis.keygo.core.domain.usecase

import de.davis.keygo.core.domain.Result
import de.davis.keygo.core.domain.asResult
import de.davis.keygo.core.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.domain.error.ValidationError
import de.davis.keygo.core.domain.model.crypto.asCryptographicData
import de.davis.keygo.core.domain.repository.MainPasswordRepository

@Deprecated("Use auth domain instead")
class ValidateMainPassword(
    private val cryptographicScopeProvider: CryptographicScopeProvider,
    private val mainPasswordRepository: MainPasswordRepository
) {

    suspend operator fun invoke(password: String): Result<Unit, ValidationError> =
        cryptographicScopeProvider.scope {
            val mainPassword = mainPasswordRepository.getMainPassword()
            val cryptographicPassword = mainPassword.hash.asCryptographicData()
            cryptographicPassword.checkHash(password.toByteArray())
        }.asResult(ValidationError.NoMatch)
}