package de.davis.keygo.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.biometrics.domain.repository.BiometricAvailabilityRepository
import de.davis.keygo.core.identity.domain.model.isUserDismissal
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.core.identity.domain.usecase.DisableBiometricsUseCase
import de.davis.keygo.core.identity.domain.usecase.EnableBiometricsUseCase
import de.davis.keygo.core.security.domain.repository.LockInfoRepository
import de.davis.keygo.core.util.combine
import de.davis.keygo.core.util.domain.model.snackbar.SnackbarMessage
import de.davis.keygo.core.util.domain.snackbar.SnackbarManager
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.presentation.UIText.Companion.ResourceString
import de.davis.keygo.feature.autofill.domain.model.AutofillActivationStatus
import de.davis.keygo.feature.autofill.domain.repository.AutofillServiceRepository
import de.davis.keygo.feature.autofill.domain.repository.ChromeAutofillRepository
import de.davis.keygo.feature.autofill.domain.usecase.AutofillActivationStatusUseCase
import de.davis.keygo.feature.backup.domain.usecase.ObserveLastBackupUseCase
import de.davis.keygo.feature.settings.R
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
    private val autofillActivationStatus: AutofillActivationStatusUseCase,
    private val lockInfoRepository: LockInfoRepository,
    private val enableBiometrics: EnableBiometricsUseCase,
    private val disableBiometrics: DisableBiometricsUseCase,
    private val snackbarManager: SnackbarManager,
    accountRepository: AccountRepository,
    appVersionRepository: AppVersionRepository,
    observeLastBackup: ObserveLastBackupUseCase,
) : ViewModel() {

    private val versionName = appVersionRepository.versionName

    private val _event = Channel<SettingsEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    // OS-owned state with no reliable change stream of its own; both are snapshotted on lifecycle
    // resume via refreshSystemState(). Autofill also gets an optimistic write on in-app disable
    // (see onEvent), since that action doesn't trigger a resume.
    private val biometricsAvailable = MutableStateFlow(false)
    private val autofillStatus = MutableStateFlow(AutofillActivationStatus())

    private val biometricsUpdating = MutableStateFlow(false)

    val state = combine(
        accountRepository.observe(),
        lockInfoRepository.observeLockInfo(),
        autofillStatus,
        biometricsAvailable,
        biometricsUpdating,
        observeLastBackup(),
    ) { account, lockInfo, autofill, biometrics, updatingBiometrics, lastBackup ->
        SettingsUiState(
            autofillEnabled = autofill.systemAutofillEnabled,
            chromeAutofillEnabled = autofill.chromeAutofillEnabled,
            biometricsAvailable = biometrics,
            biometricsEnabled = biometrics && account?.biometricWrappedArk != null,
            biometricsUpdating = updatingBiometrics,
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
        viewModelScope.launch {
            val status = autofillActivationStatus()
            autofillStatus.update { status }
        }
    }

    fun onEvent(event: SettingsUiEvent) {
        when (event) {
            is SettingsUiEvent.SetBiometrics -> updatingBiometrics {
                when {
                    event.enabled -> enableBiometrics()
                    else -> disableBiometrics()
                }.onFailure { error ->
                    if (error.isUserDismissal()) return@onFailure

                    snackbarManager.sendMessage(
                        SnackbarMessage(message = ResourceString(R.string.settings_biometric_update_failed))
                    )
                }
            }

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
                    autofillStatus.update { it.copy(systemAutofillEnabled = false) }
                }
            }

            SettingsUiEvent.OpenChromeAutofillSettings -> chromeAutofillRepository.openChromeAutofillSettings()

            SettingsUiEvent.ResetPassword -> _event.trySend(SettingsEvent.NavigateToChangePassword)

            SettingsUiEvent.OpenBackup -> _event.trySend(SettingsEvent.NavigateToBackup)

            SettingsUiEvent.LibrariesClicked -> _event.trySend(SettingsEvent.NavigateToLibraries)
            SettingsUiEvent.ReportIssue -> _event.trySend(SettingsEvent.ReportIssue)
        }
    }

    private fun updatingBiometrics(block: suspend () -> Unit) {
        // One update at a time: a toggle that lands while the prompt is still open would otherwise
        // run against an account the first update has not written yet.
        if (!biometricsUpdating.compareAndSet(expect = false, update = true)) return

        viewModelScope.launch {
            try {
                block()
            } finally {
                biometricsUpdating.update { false }
            }
        }
    }
}
