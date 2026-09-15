package de.davis.keygo.feature.settings.presentation.changepassword

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.delete
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.identity.domain.model.ChangePasswordError
import de.davis.keygo.core.identity.domain.model.Reauthentication
import de.davis.keygo.core.identity.domain.model.UnlockableByBiometricsResult
import de.davis.keygo.core.identity.domain.usecase.ChangePasswordUseCase
import de.davis.keygo.core.identity.domain.usecase.UnlockableByBiometricsUseCase
import de.davis.keygo.core.item.domain.estimator.PasswordStrengthEstimator
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.ui.model.UiFieldError
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
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
import kotlin.time.Duration.Companion.milliseconds

@KoinViewModel
internal class ChangePasswordViewModel(
    private val unlockableByBiometrics: UnlockableByBiometricsUseCase,
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
            val available = unlockableByBiometrics() == UnlockableByBiometricsResult.Available
            _state.update { it.copy(biometricAvailable = available) }
        }
    }

    fun onSubmit(forcePasswordPath: Boolean = false) {
        change(withPassword = forcePasswordPath || !_state.value.biometricAvailable)
    }

    fun dismissReauthDialog() {
        _state.update { it.copy(showReauthDialog = false, currentPasswordError = null) }
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

    private fun change(withPassword: Boolean = false) {
        if (!validateNewPasswords()) return

        val current = _state.value.currentPassword.text.toString()
        if (withPassword && current.isBlank()) {
            _state.update { it.copy(currentPasswordError = UiFieldError.Empty) }
            return
        }

        val reauthentication = if (withPassword) Reauthentication.Password(current)
        else Reauthentication.Biometric

        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            changePassword(reauthentication, _state.value.newPassword.text.toString())
                .onSuccess { _event.trySend(ChangePasswordEvent.Success) }
                .onFailure(::handleFailure)
            _state.update { it.copy(loading = false) }
        }
    }

    private fun handleFailure(error: ChangePasswordError) {
        when (error) {
            ChangePasswordError.IncorrectPassword ->
                _state.update { it.copy(currentPasswordError = UiFieldError.Incorrect) }

            // Any prompt that did not hand back the live ARK falls back to the master password.
            ChangePasswordError.BiometricDeclined,
            ChangePasswordError.BiometricAuthFailed,
                -> _state.update { it.copy(showReauthDialog = true) }

            ChangePasswordError.BiometricCanceled -> Unit

            else -> _event.trySend(ChangePasswordEvent.GenericError)
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    private fun clearSensitiveFields() {
        _state.value.currentPassword.edit { delete(0, length) }
        _state.value.currentPassword.undoState.clearHistory()
        _state.value.newPassword.edit { delete(0, length) }
        _state.value.newPassword.undoState.clearHistory()
        _state.value.confirmPassword.edit { delete(0, length) }
        _state.value.confirmPassword.undoState.clearHistory()

        _state.update {
            it.copy(
                currentPasswordError = null,
                newPasswordError = null,
                confirmPasswordError = null,
                showReauthDialog = false,
            )
        }
    }
}
