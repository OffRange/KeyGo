package de.davis.keygo.feature.settings.presentation.changepassword

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.identity.domain.model.ChangePasswordError
import de.davis.keygo.core.identity.domain.model.Reauthentication
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.core.identity.domain.usecase.ChangePasswordUseCase
import de.davis.keygo.core.item.domain.estimator.PasswordStrengthEstimator
import de.davis.keygo.core.security.domain.model.BiometricAuthError
import de.davis.keygo.core.security.domain.model.CiphertextData
import de.davis.keygo.core.security.domain.repository.BiometricAvailabilityRepository
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
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
) : ViewModel() {

    private val _state = MutableStateFlow(ChangePasswordState())
    val state = _state.asStateFlow()

    // Buffered (not rendezvous): Success/GenericError are emitted from a background coroutine that
    // may complete before a collector subscribes; a one-shot navigation/error signal must not drop.
    private val _event = Channel<ChangePasswordEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    init {
        resolveBiometricAvailability()
        observePasswordStrength()
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

    @OptIn(FlowPreview::class)
    private fun observePasswordStrength() {
        snapshotFlow { _state.value.newPassword.text }
            .debounce(150.milliseconds)
            .distinctUntilChanged()
            .onEach { text ->
                val score = passwordStrengthEstimator(text.toString())
                _state.update { it.copy(passwordScore = score) }
            }
            .launchIn(viewModelScope)
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
            _state.update { it.copy(currentPasswordError = FieldError.Empty) }
            return
        }
        change(Reauthentication.Password(current))
    }

    /** Dismiss the fallback dialog and clear any stale current-password error. */
    fun dismissReauthDialog() {
        _state.update { it.copy(showReauthDialog = false, currentPasswordError = FieldError.None) }
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
                currentPasswordError = FieldError.None,
                newPasswordError = FieldError.None,
                confirmPasswordError = FieldError.None,
            )
        }
        if (new.isBlank()) {
            _state.update { it.copy(newPasswordError = FieldError.Empty) }
            return false
        }
        if (new != confirm) {
            _state.update { it.copy(confirmPasswordError = FieldError.Mismatch) }
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
                _state.update { it.copy(currentPasswordError = FieldError.Incorrect) }

            else -> _event.trySend(ChangePasswordEvent.GenericError)
        }
    }
}
