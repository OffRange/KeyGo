package de.davis.keygo.feature.settings.presentation.changepassword

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.identity.domain.model.ChangePasswordError
import de.davis.keygo.core.identity.domain.model.Reauthentication
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.core.identity.domain.usecase.ChangePasswordUseCase
import de.davis.keygo.core.item.domain.estimator.PasswordStrengthEstimator
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.model.BiometricAuthError
import de.davis.keygo.core.security.domain.model.CiphertextData
import de.davis.keygo.core.security.domain.repository.BiometricAvailabilityRepository
import de.davis.keygo.core.ui.model.UiFieldError
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import java.security.Key
import kotlin.time.Duration.Companion.milliseconds

@KoinViewModel
internal class ChangePasswordViewModel(
    private val accountRepository: AccountRepository,
    private val biometricAvailabilityRepository: BiometricAvailabilityRepository,
    private val passwordStrengthEstimator: PasswordStrengthEstimator,
    private val changePassword: ChangePasswordUseCase,
    private val session: Session,
) : ViewModel() {

    private val _state = MutableStateFlow(ChangePasswordState())

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val passwordStrength = snapshotFlow { _state.value.newPassword.text }
        .debounce(150.milliseconds)
        .distinctUntilChanged()
        .mapLatest { text ->
            passwordStrengthEstimator(text.toString())
        }
        // combine withholds its first emission until every input has emitted, so without a value
        // up front the form would sit on initialValue until the debounce elapses.
        .onStart { emit(PasswordScore.None) }

    val state = combine(_state, passwordStrength) { baseState, score ->
        baseState.copy(passwordScore = score)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _state.value,
    )

    // Buffered (not rendezvous): Success/GenericError are emitted from a background coroutine that
    // may complete before a collector subscribes; a one-shot navigation/error signal must not drop.
    private val _event = Channel<ChangePasswordEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    init {
        resolveBiometricAvailability()
        viewModelScope.launch {
            session.isActive.filter { !it }.collect { clearSensitiveFields() }
        }
    }

    private fun resolveBiometricAvailability() {
        viewModelScope.launch {
            val wrapped = accountRepository.getOrNull()?.biometricWrappedArk
            val available = biometricAvailabilityRepository.availability()
            if (wrapped == null || !available) return@launch
            _state.update {
                it.copy(
                    biometricCiphertext = CiphertextData(
                        bytes = wrapped.key,
                        iv = wrapped.keyIV
                    )
                )
            }
        }
    }

    /**
     * Primary "Change password" action. Validates the new passwords first so we never fire a
     * biometric prompt for an invalid form, then routes to biometric (if available) or the
     * typed-password path.
     */
    fun onSubmit() {
        if (_state.value.canUseBiometric) {
            if (!validateNewPasswords()) return
            _event.trySend(ChangePasswordEvent.LaunchBiometricPrompt)
            return
        }
        submitWithPassword()
    }

    /** Verify with the typed current password, then change. */
    fun submitWithPassword() {
        val state = _state.value
        val current = state.currentPassword.text.toString()
        if (!validateNewPasswords()) return
        if (current.isBlank()) {
            _state.update { it.copy(currentPasswordError = UiFieldError.Empty) }
            return
        }
        change(Reauthentication.Password(current))
    }

    /** Dismiss the fallback dialog and clear any stale current-password error. */
    fun dismissReauthDialog() {
        _state.update { it.copy(showReauthDialog = false, currentPasswordError = null) }
    }

    /** Verify with biometric: [recoveredArk] was unwrapped by the screen via requestUnwrap. */
    private fun submitWithBiometric(recoveredArk: ByteArray) {
        if (!validateNewPasswords()) {
            recoveredArk.fill(0)
            return
        }
        change(Reauthentication.Biometric(recoveredArk))
    }

    /**
     * Route the outcome of the screen's biometric prompt. The screen owns the prompt (it needs an
     * Activity), but interpreting the result is ours: a recovered key changes the password, while a
     * declined / locked-out / failed prompt falls back to the master-password dialog.
     */
    fun onBiometricResult(result: Result<Key, BiometricAuthError>) {
        when (result) {
            is Result.Success -> {
                // requestUnwrap returns a software SecretKeySpec (AES), so raw bytes always exist.
                val recoveredArk = checkNotNull(result.success.encoded)
                submitWithBiometric(recoveredArk)
            }

            is Result.Failure -> when (result.error) {
                BiometricAuthError.Declined,
                BiometricAuthError.LockedOut,
                BiometricAuthError.NoCipher,
                is BiometricAuthError.CanNotAuthenticate,
                    -> _state.update { it.copy(showReauthDialog = true) }

                // Transient dismissal (back press, system cancel, timeout): leave the form as-is.
                BiometricAuthError.Canceled,
                is BiometricAuthError.Unknown,
                    -> Unit
            }
        }
    }

    private fun validateNewPasswords(): Boolean {
        val new = _state.value.newPassword.text.toString()
        val confirm = _state.value.confirmPassword.text.toString()
        _state.update {
            it.copy(
                currentPasswordError = null,
                newPasswordError = null,
                confirmPasswordError = null,
            )
        }
        if (new.isBlank()) {
            _state.update { it.copy(newPasswordError = UiFieldError.Empty) }
            return false
        }
        if (new != confirm) {
            _state.update { it.copy(confirmPasswordError = UiFieldError.Mismatch) }
            return false
        }
        return true
    }

    private fun change(reauth: Reauthentication) {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            changePassword(reauth, _state.value.newPassword.text.toString())
                .onSuccess { _event.trySend(ChangePasswordEvent.Success) }
                .onFailure(::handleFailure)
            _state.update { it.copy(loading = false) }
        }
    }

    private fun handleFailure(error: ChangePasswordError) {
        when (error) {
            ChangePasswordError.IncorrectPassword ->
                _state.update { it.copy(currentPasswordError = UiFieldError.Incorrect) }

            else -> _event.trySend(ChangePasswordEvent.GenericError)
        }
    }

    /**
     * The current password is the RootKek derivation input - the one secret this codebase never
     * retains anywhere, including across a lock. Everything else about the screen is left alone,
     * the same as any other screen the app leaves in place while locked.
     *
     * Mutates the existing [TextFieldState] instances rather than replacing them: [passwordStrength]
     * tracks a snapshot-state read on whichever instance `_state.value.newPassword` pointed to the
     * last time it ran, and a fresh replacement instance's changes would go unobserved - the old,
     * abandoned instance is simply never mutated, so nothing ever re-triggers the flow, and
     * [ChangePasswordState.passwordScore] would freeze at whatever it was the moment before the
     * clear for the rest of the ViewModel's life.
     */
    private fun clearSensitiveFields() {
        _state.value.currentPassword.edit { delete(0, length) }
        _state.value.newPassword.edit { delete(0, length) }
        _state.value.confirmPassword.edit { delete(0, length) }
    }
}
