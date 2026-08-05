package de.davis.keygo.app.presentation

import androidx.lifecycle.ViewModel
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.migration.create_access.domain.usecase.HasMainPasswordUseCase
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class AppViewModel(
    private val hasV1Password: HasMainPasswordUseCase,
    private val accountRepository: AccountRepository
) : ViewModel() {

    suspend fun hasValidAccount() = accountRepository.getOrNull() != null && !hasV1Password()
}