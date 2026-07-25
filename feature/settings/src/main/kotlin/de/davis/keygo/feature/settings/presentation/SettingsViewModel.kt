package de.davis.keygo.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.core.security.domain.repository.BiometricAvailabilityRepository
import de.davis.keygo.feature.autofill.domain.repository.AutofillServiceRepository
import de.davis.keygo.feature.backup.domain.usecase.ObserveLastBackupUseCase
import de.davis.keygo.feature.settings.domain.model.OsState
import de.davis.keygo.feature.settings.domain.repository.AppVersionRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class SettingsViewModel(
    private val biometricAvailabilityRepository: BiometricAvailabilityRepository,
    private val autofillServiceRepository: AutofillServiceRepository,
    accountRepository: AccountRepository,
    appVersionRepository: AppVersionRepository,
    observeLastBackup: ObserveLastBackupUseCase,
) : ViewModel() {

    private val versionName = appVersionRepository.versionName

    // Buffered (not rendezvous): the screen handles events in a suspend collector (e.g. while the
    // biometric enrollment prompt is open), and a rendezvous trySend would silently drop any tap
    // made in the meantime.
    private val _event = Channel<SettingsEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    // OS-owned state (autofill / biometric availability) has no observable stream of
    // its own; we snapshot it on lifecycle resume via refreshSystemState().
    private val osState = MutableStateFlow(OsState())

    val state = combine(
        accountRepository.observe(),
        osState,
        observeLastBackup(),
    ) { account, os, lastBackup ->
        SettingsUiState(
            autofillEnabled = os.autofillEnabled,
            biometricsAvailable = os.biometricsAvailable,
            biometricsEnabled = os.biometricsAvailable && account?.biometricWrappedArk != null,
            version = versionName,
            lastBackupAt = lastBackup?.finishedAt,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(version = versionName),
    )

    fun refreshSystemState() {
        osState.update {
            OsState(
                autofillEnabled = autofillServiceRepository.isEnabled(),
                biometricsAvailable = biometricAvailabilityRepository.availability(),
            )
        }
    }

    fun onEvent(event: SettingsUiEvent) {
        when (event) {
            is SettingsUiEvent.SetBiometrics -> _event.trySend(SettingsEvent.EnableBiometric(event.enabled))
            is SettingsUiEvent.SetAutofill -> when {
                event.enabledRequest -> _event.trySend(SettingsEvent.OpenAutofillSelection)
                else -> {
                    autofillServiceRepository.disable()
                    // disableAutofillServices() propagates through the system server
                    // asynchronously, so re-polling isEnabled() here can still read true.
                    // The outcome is deterministic, so update the snapshot directly.
                    osState.update { it.copy(autofillEnabled = false) }
                }
            }

            SettingsUiEvent.ResetPassword -> _event.trySend(SettingsEvent.NavigateToChangePassword)

            SettingsUiEvent.OpenBackup -> _event.trySend(SettingsEvent.NavigateToBackup)

            SettingsUiEvent.LibrariesClicked -> _event.trySend(SettingsEvent.NavigateToLibraries)
            SettingsUiEvent.ReportIssue -> _event.trySend(SettingsEvent.ReportIssue)
        }
    }
}
