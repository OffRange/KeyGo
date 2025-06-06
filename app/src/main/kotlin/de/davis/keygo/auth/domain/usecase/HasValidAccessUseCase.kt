package de.davis.keygo.auth.domain.usecase

import de.davis.keygo.auth.di.annotation.PasswordQualifier
import de.davis.keygo.auth.domain.repository.PasswordWrappedKeyRepository
import org.koin.core.annotation.Single

@Single
class HasValidAccessUseCase(
    @PasswordQualifier
    private val wrappedKeyRepository: PasswordWrappedKeyRepository,
) {

    suspend operator fun invoke(): Boolean =
        wrappedKeyRepository.getWrappedKeyData()?.isValid() == true
}