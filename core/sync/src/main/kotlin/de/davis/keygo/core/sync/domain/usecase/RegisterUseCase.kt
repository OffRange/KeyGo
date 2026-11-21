package de.davis.keygo.core.sync.domain.usecase

import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.core.sync.domain.model.RegisterError
import de.davis.keygo.core.sync.domain.model.SyncAccount
import de.davis.keygo.core.sync.domain.repository.SyncRepository
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.mapFailure
import org.koin.core.annotation.Single

@Single
class RegisterUseCase(
    private val syncRepository: SyncRepository,
    private val accountRepository: AccountRepository,
) {

    suspend operator fun invoke(
        serverUrl: String,
        username: String,
        password: String,
        invite: String?,
    ): Result<SyncAccount, RegisterError> {
        val userId = accountRepository.getOrNull()?.id
            ?: return Result.Failure(RegisterError.ActiveAccountNotFound)

        return syncRepository.register(
            serverUrl = serverUrl,
            userId = userId,
            username = username,
            password = password,
            invite = invite,
        ).mapFailure(RegisterError::Sync)
    }
}
