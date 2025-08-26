package de.davis.keygo.core.domain.usecase

import de.davis.keygo.auth.domain.repository.PasswordWrappedKeyRepository
import de.davis.keygo.core.di.annotation.PasswordQualifier
import org.koin.core.annotation.Single

@Single
class HasValidAccessUseCase(
    @PasswordQualifier
    private val wrappedKeyRepository: PasswordWrappedKeyRepository,
) {

    suspend operator fun invoke(): Boolean =
        wrappedKeyRepository.getWrappedKeyData()?.isValid() == true
}