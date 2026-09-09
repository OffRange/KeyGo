package de.davis.keygo.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.legacy_migration.domain.usecase.HasMainPasswordUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class AppViewModel(
    private val accountRepository: AccountRepository,
    private val hasV1Password: HasMainPasswordUseCase,
    session: Session,
) : ViewModel() {

    private val _isReturningUser = MutableStateFlow<Boolean?>(null)
    val isReturningUser = _isReturningUser.asStateFlow()

    /**
     * The session's raw state, which [MainActivity] gates on: no ARK means the user has to
     * authenticate again, whether the session just ended or a restored process never had one.
     */
    val isSessionActive: StateFlow<Boolean> = session.isActive

    init {
        viewModelScope.launch {
            _isReturningUser.update { accountRepository.getOrNull() != null || hasV1Password() }
        }
    }
}
