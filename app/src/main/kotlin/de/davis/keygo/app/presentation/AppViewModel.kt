package de.davis.keygo.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.migration.create_access.domain.usecase.HasMainPasswordUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class AppViewModel(
    private val accountRepository: AccountRepository,
    private val hasV1Password: HasMainPasswordUseCase,
) : ViewModel() {

    private val _isReturningUser = MutableStateFlow<Boolean?>(null)
    val isReturningUser = _isReturningUser.asStateFlow()

    init {
        viewModelScope.launch {
            _isReturningUser.update { accountRepository.getOrNull() != null || hasV1Password() }
        }
    }
}