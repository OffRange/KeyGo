package de.davis.keygo.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.core.security.domain.repository.BiometricAvailabilityRepository
import de.davis.keygo.core.security.domain.repository.LockInfoRepository
import de.davis.keygo.core.util.combine
import de.davis.keygo.feature.autofill.domain.repository.AutofillServiceRepository
import de.davis.keygo.feature.autofill.domain.repository.ChromeAutofillRepository
import de.davis.keygo.feature.backup.domain.usecase.ObserveLastBackupUseCase
import de.davis.keygo.feature.settings.domain.repository.AppVersionRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class SettingsViewModel(
    private val biometricAvailabilityRepository: BiometricAvailabilityRepository,
    private val autofillServiceRepository: AutofillServiceRepository,
    private val chromeAutofillRepository: ChromeAutofillRepository,
    private val lockInfoRepository: LockInfoRepository,
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

    // OS-owned state with no reliable change stream of its own; both are snapshotted on lifecycle
    // resume via refreshSystemState(). Autofill also gets an optimistic write on in-app disable
    // (see onEvent), since that action doesn't trigger a resume.
    private val biometricsAvailable = MutableStateFlow(false)
    private val autofillEnabled = MutableStateFlow(false)
    private val chromeAutofillEnabled = MutableStateFlow(false)

    val state = combine(
        accountRepository.observe(),
        lockInfoRepository.observeLockInfo(),
        autofillEnabled,
        chromeAutofillEnabled,
        biometricsAvailable,
        observeLastBackup(),
    ) { account, lockInfo, autofill, chromeAutofill, biometrics, lastBackup ->
        SettingsUiState(
            autofillEnabled = autofill,
            chromeAutofillEnabled = chromeAutofill,
            biometricsAvailable = biometrics,
            biometricsEnabled = biometrics && account?.biometricWrappedArk != null,
            version = versionName,
            lastBackupAt = lastBackup?.finishedAt,
            lockTimeout = lockInfo.autoLockTimeout,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(version = versionName),
    )

    fun refreshSystemState() {
        biometricsAvailable.update { biometricAvailabilityRepository.availability() }
        // Re-read on resume: the autofill selection changes in the system picker/settings, which
        // run in a separate activity, so this is where we learn KeyGo was enabled or disabled.
        autofillEnabled.update { autofillServiceRepository.isEnabled() }
        viewModelScope.launch {
            chromeAutofillEnabled.update { chromeAutofillRepository.isAutofillEnabled() }
        }
    }

    fun onEvent(event: SettingsUiEvent) {
        when (event) {
            is SettingsUiEvent.SetBiometrics -> _event.trySend(SettingsEvent.EnableBiometric(event.enabled))

            is SettingsUiEvent.SetAutoLockTimeout -> viewModelScope.launch {
                lockInfoRepository.setAutoLockTimeout(event.timeout)
            }

            is SettingsUiEvent.SetAutofill -> when {
                event.enabledRequest -> _event.trySend(SettingsEvent.OpenAutofillSelection)
                else -> {
                    autofillServiceRepository.disable()
                    // disable() propagates through the system server asynchronously and this action
                    // doesn't trigger a resume, so reflect the intent immediately; the next resume
                    // re-read confirms it.
                    autofillEnabled.update { false }
                }
            }

            SettingsUiEvent.OpenChromeAutofillSettings -> chromeAutofillRepository.openChromeAutofillSettings()

            SettingsUiEvent.ResetPassword -> _event.trySend(SettingsEvent.NavigateToChangePassword)

            SettingsUiEvent.OpenBackup -> _event.trySend(SettingsEvent.NavigateToBackup)

            SettingsUiEvent.LibrariesClicked -> _event.trySend(SettingsEvent.NavigateToLibraries)
            SettingsUiEvent.ReportIssue -> _event.trySend(SettingsEvent.ReportIssue)
        }
    }
}
