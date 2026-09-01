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
     * A restored back stack can land straight in the authenticated graph after process death,
     * skipping AuthRoute; the fresh process's [Session] is never unlocked in that case, and
     * nothing else routes back to AuthRoute since it's popped inclusive on login success.
     * [MainActivity] observes this and redirects whenever it goes false.
     */
    val isSessionActive: StateFlow<Boolean> = session.isActive

    init {
        viewModelScope.launch {
            _isReturningUser.update { accountRepository.getOrNull() != null || hasV1Password() }
        }
    }
}