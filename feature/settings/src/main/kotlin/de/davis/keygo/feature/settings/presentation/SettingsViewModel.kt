package de.davis.keygo.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.core.security.domain.repository.BiometricAvailabilityRepository
import de.davis.keygo.feature.autofill.domain.repository.AutofillServiceRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class SettingsViewModel(
    private val biometricAvailabilityRepository: BiometricAvailabilityRepository,
    private val accountRepository: AccountRepository,
    private val autofillServiceRepository: AutofillServiceRepository,
) : ViewModel() {

    private val _event = Channel<SettingsEvent>()
    val event = _event.receiveAsFlow()

    private val _state = MutableStateFlow(SettingsUiState())
    val state = _state.asStateFlow()

    init {
        reset()
    }

    fun reset() {
        viewModelScope.launch {
            val autofillEnabled = autofillServiceRepository.isEnabled()

            val biometricsAvailable = biometricAvailabilityRepository.availability()
            val biometricsEnabled =
                biometricsAvailable && accountRepository.getOrNull()?.biometricWrappedArk != null

            _state.update {
                it.copy(
                    autofillEnabled = autofillEnabled,
                    biometricsAvailable = biometricsAvailable,
                    biometricsEnabled = biometricsEnabled,
                )
            }
        }
    }

    fun onEvent(event: SettingsUiEvent) {
        when (event) {
            is SettingsUiEvent.SetBiometrics -> {}

            is SettingsUiEvent.SetAutofill -> {
                _event.trySend(SettingsEvent.OpenAutofillSelection)
            }

            SettingsUiEvent.ResetPassword -> {}
        }
    }
}