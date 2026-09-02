package de.davis.keygo.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.legacy_migration.domain.usecase.HasMainPasswordUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
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
     * True exactly when a session that was active has just ended - never at first launch, before
     * the session has ever been active. A level read of "not active" would also be true before the
     * very first login, before [MainActivity.launchRoute]'s onboarding or deep-link auth screen has
     * had a chance to show, and would clobber it. [MainActivity] observes this to put the re-auth
     * gate up.
     */
    val isLocked: StateFlow<Boolean> = session.isActive
        .scan(false to false) { (wasActive, _), isActive -> isActive to (wasActive && !isActive) }
        .map { it.second }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    /**
     * The session's raw current state, for [MainActivity] to self-heal a back stack a
     * configuration change or process death restored straight into the app proper with a session
     * that never got re-established. [isLocked] alone cannot catch this: it only fires on a
     * transition, and a freshly restored [AppViewModel] has no memory of the session ever having
     * been active to transition from.
     */
    val isSessionActive: StateFlow<Boolean> = session.isActive

    init {
        viewModelScope.launch {
            _isReturningUser.update { accountRepository.getOrNull() != null || hasV1Password() }
        }
    }
}
