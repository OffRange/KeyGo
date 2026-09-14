@file:OptIn(ExportArk::class)

package de.davis.keygo.feature.settings.presentation

import de.davis.keygo.core.biometrics.FakeBiometricAvailabilityRepository
import de.davis.keygo.core.biometrics.FakeBiometricCrypto
import de.davis.keygo.core.biometrics.domain.model.BiometricAuthError
import de.davis.keygo.core.feature.autofill.FakeAutofillServiceRepository
import de.davis.keygo.core.feature.autofill.FakeChromeAutofillRepository
import de.davis.keygo.core.feature.settings.FakeAppVersionRepository
import de.davis.keygo.core.identity.FakeAccountRepository
import de.davis.keygo.core.identity.domain.mapper.toBiometricWrappedArk
import de.davis.keygo.core.identity.domain.model.Account
import de.davis.keygo.core.identity.domain.model.PasswordWrappedArk
import de.davis.keygo.core.identity.domain.usecase.DisableBiometricsUseCase
import de.davis.keygo.core.identity.domain.usecase.EnableBiometricsUseCase
import de.davis.keygo.core.security.FakeLockInfoRepository
import de.davis.keygo.core.security.FakeSession
import de.davis.keygo.core.security.crypto.FakeKeyStoreManager
import de.davis.keygo.core.security.domain.ExportArk
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.security.domain.model.LockInfo
import de.davis.keygo.core.util.FakeSnackbarManager
import de.davis.keygo.core.util.assertSuccess
import de.davis.keygo.core.util.domain.model.snackbar.SnackbarMessage
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.presentation.UIText
import de.davis.keygo.feature.autofill.domain.usecase.AutofillActivationStatusUseCase
import de.davis.keygo.feature.backup.FakeBackupJobRepository
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.domain.model.BackupJob
import de.davis.keygo.feature.backup.domain.model.BackupResult
import de.davis.keygo.feature.backup.domain.model.FileFormat
import de.davis.keygo.feature.backup.domain.usecase.ObserveLastBackupUseCase
import de.davis.keygo.feature.settings.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val accountRepository = FakeAccountRepository()
    private val biometricAvailability = FakeBiometricAvailabilityRepository()
    private val autofillServiceRepository = FakeAutofillServiceRepository()
    private val chromeAutofillRepository = FakeChromeAutofillRepository()
    private val appVersionRepository = FakeAppVersionRepository()
    private val backupJobRepository = FakeBackupJobRepository()
    private val lockInfoRepository = FakeLockInfoRepository()
    private val session = FakeSession(startUnlocked = true)
    private val keyStoreManager = FakeKeyStoreManager()
    private val biometricCrypto = FakeBiometricCrypto(keyStoreManager)
    private val snackbarManager = FakeSnackbarManager()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = SettingsViewModel(
        biometricAvailabilityRepository = biometricAvailability,
        autofillServiceRepository = autofillServiceRepository,
        chromeAutofillRepository = chromeAutofillRepository,
        autofillActivationStatus = AutofillActivationStatusUseCase(
            autofillServiceRepository = autofillServiceRepository,
            chromeAutofillRepository = chromeAutofillRepository,
        ),
        lockInfoRepository = lockInfoRepository,
        enableBiometrics = EnableBiometricsUseCase(
            accountRepository = accountRepository,
            session = session,
            keyStoreManager = keyStoreManager,
            biometricCrypto = biometricCrypto,
        ),
        disableBiometrics = DisableBiometricsUseCase(accountRepository, keyStoreManager),
        snackbarManager = snackbarManager,
        accountRepository = accountRepository,
        appVersionRepository = appVersionRepository,
        observeLastBackup = ObserveLastBackupUseCase(backupJobRepository),
    )

    private val biometricUpdateFailed =
        SnackbarMessage(message = UIText.ResourceString(R.string.settings_biometric_update_failed))

    private suspend fun seedAccount(enrolled: Boolean) {
        biometricAvailability.isAvailable = true
        val ark = checkNotNull(session.exportArk().getOrNull())
        accountRepository.seed(
            Account(
                id = UUID.randomUUID(),
                displayName = "Test",
                passwordWrappedArk = PasswordWrappedArk(
                    key = ByteArray(48) { 1 },
                    keyIV = ByteArray(12) { 2 },
                    salt = ByteArray(16) { 3 },
                ),
                biometricWrappedArk = if (enrolled) {
                    biometricCrypto.requestWrap(KeyId.BiometricVaultKek) { seal -> seal(ark) }
                        .assertSuccess()
                        .toBiometricWrappedArk()
                } else null,
            ),
        )
        biometricCrypto.prompts.clear()
    }

    @Test
    fun `requesting to enable autofill emits OpenAutofillSelection and does not disable the service`() =
        runTest(dispatcher) {
            autofillServiceRepository.enabled = false
            val vm = viewModel()

            vm.onEvent(SettingsUiEvent.SetAutofill(enabledRequest = true))

            assertEquals(SettingsEvent.OpenAutofillSelection, vm.event.first())
            assertFalse(autofillServiceRepository.disableCalled)
        }

    @Test
    fun `requesting to disable autofill disables the service and updates the ui`() =
        runTest(dispatcher) {
            autofillServiceRepository.enabled = true
            val vm = viewModel()
            vm.refreshSystemState()
            vm.state.first { it.autofillEnabled }

            vm.onEvent(SettingsUiEvent.SetAutofill(enabledRequest = false))

            assertTrue(autofillServiceRepository.disableCalled)
            vm.state.first { !it.autofillEnabled }
        }

    @Test
    fun `refreshSystemState reflects an enable made from the system picker while backgrounded`() =
        runTest(dispatcher) {
            autofillServiceRepository.enabled = false
            val vm = viewModel()
            vm.refreshSystemState()
            vm.state.first { !it.autofillEnabled }

            // The user selected KeyGo from the system picker (a separate activity); on returning,
            // LifecycleResumeEffect calls refreshSystemState() and the chip must follow.
            autofillServiceRepository.enabled = true
            vm.refreshSystemState()

            vm.state.first { it.autofillEnabled }
        }

    @Test
    fun `switching biometrics on enrolls the account`() = runTest(dispatcher) {
        seedAccount(enrolled = false)
        val vm = viewModel()
        vm.refreshSystemState()

        vm.onEvent(SettingsUiEvent.SetBiometrics(enabled = true))

        vm.state.first { it.biometricsEnabled }
        assertNotNull(accountRepository.getOrNull()?.biometricWrappedArk)
        assertEquals(1, biometricCrypto.prompts.size)
        assertTrue(snackbarManager.messages.isEmpty())
    }

    @Test
    fun `switching biometrics off drops the enrollment without prompting`() = runTest(dispatcher) {
        seedAccount(enrolled = true)
        val vm = viewModel()
        vm.refreshSystemState()
        vm.state.first { it.biometricsEnabled }

        vm.onEvent(SettingsUiEvent.SetBiometrics(enabled = false))

        vm.state.first { !it.biometricsEnabled }
        assertNull(accountRepository.getOrNull()?.biometricWrappedArk)
        assertFalse(KeyId.BiometricVaultKek in keyStoreManager.keys)
        assertTrue(biometricCrypto.prompts.isEmpty())
        assertTrue(snackbarManager.messages.isEmpty())
    }

    /**
     * The switch only moves once the stored enrollment does, so a second tap while the prompt is
     * open reads as the opposite request. Run alongside the enable, a disable deletes the key the
     * prompt is bound to.
     */
    @Test
    fun `a biometrics toggle made while an enrollment is running is dropped`() =
        runTest(dispatcher) {
            seedAccount(enrolled = false)
            val prompt = CompletableDeferred<Unit>()
            biometricCrypto.pendingPrompt = prompt
            val vm = viewModel()
            vm.state.launchIn(backgroundScope)

            vm.onEvent(SettingsUiEvent.SetBiometrics(enabled = true))
            advanceUntilIdle()
            assertTrue(vm.state.value.biometricsUpdating)

            vm.onEvent(SettingsUiEvent.SetBiometrics(enabled = false))
            advanceUntilIdle()
            prompt.complete(Unit)
            advanceUntilIdle()

            assertEquals(1, accountRepository.setCount)
            assertNotNull(accountRepository.getOrNull()?.biometricWrappedArk)
            assertFalse(vm.state.value.biometricsUpdating)
        }

    @Test
    fun `backing out of the enrollment prompt is not reported`() = runTest(dispatcher) {
        listOf(BiometricAuthError.Declined, BiometricAuthError.Canceled).forEach { dismissal ->
            seedAccount(enrolled = false)
            biometricCrypto.promptFailure = dismissal
            val vm = viewModel()

            vm.onEvent(SettingsUiEvent.SetBiometrics(enabled = true))
            advanceUntilIdle()

            assertNull(accountRepository.getOrNull()?.biometricWrappedArk, "$dismissal")
            assertTrue(snackbarManager.messages.isEmpty(), "$dismissal")
        }
    }

    @Test
    fun `an enrollment the prompt could not complete is reported`() = runTest(dispatcher) {
        seedAccount(enrolled = false)
        biometricCrypto.promptFailure = BiometricAuthError.LockedOut
        val vm = viewModel()

        vm.onEvent(SettingsUiEvent.SetBiometrics(enabled = true))
        advanceUntilIdle()

        assertEquals(listOf(biometricUpdateFailed), snackbarManager.messages)
        assertNull(accountRepository.getOrNull()?.biometricWrappedArk)
    }

    @Test
    fun `a disable that cannot be saved is reported and leaves the enrollment on`() =
        runTest(dispatcher) {
            seedAccount(enrolled = true)
            accountRepository.setFails = true
            val vm = viewModel()
            vm.state.launchIn(backgroundScope)
            vm.refreshSystemState()

            vm.onEvent(SettingsUiEvent.SetBiometrics(enabled = false))
            advanceUntilIdle()

            assertEquals(listOf(biometricUpdateFailed), snackbarManager.messages)
            assertTrue(vm.state.value.biometricsEnabled)
            assertTrue(KeyId.BiometricVaultKek in keyStoreManager.keys)
        }

    @Test
    fun `biometrics read as off once the device can no longer use them`() = runTest(dispatcher) {
        seedAccount(enrolled = true)
        val vm = viewModel()
        vm.state.launchIn(backgroundScope)
        vm.refreshSystemState()
        advanceUntilIdle()
        assertTrue(vm.state.value.biometricsEnabled)

        biometricAvailability.isAvailable = false
        vm.refreshSystemState()
        advanceUntilIdle()

        assertFalse(vm.state.value.biometricsEnabled)
    }

    @Test
    fun `opening backup emits NavigateToBackup`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onEvent(SettingsUiEvent.OpenBackup)

        assertEquals(SettingsEvent.NavigateToBackup, vm.event.first())
    }

    @Test
    fun `state carries the newest successful backup timestamp`() = runTest(dispatcher) {
        backupJobRepository.jobs["one-time"] = BackupJob(
            uri = BackupDestinationUri("content://backup.json"),
            wrappedPassphrase = null,
            format = FileFormat.JSON,
            finishedAt = 1_700_000_000_000L,
            lastResult = BackupResult.Success,
        )
        val vm = viewModel()

        assertEquals(1_700_000_000_000L, vm.state.first { it.lastBackupAt != null }.lastBackupAt)
    }

    @Test
    fun `state reports no last backup while none has completed`() = runTest(dispatcher) {
        val vm = viewModel()

        assertNull(vm.state.first().lastBackupAt)
    }

    @Test
    fun `refreshSystemState reflects the chrome autofill state from the repository`() =
        runTest(dispatcher) {
            chromeAutofillRepository.enabled = true
            val vm = viewModel()

            vm.refreshSystemState()

            vm.state.first { it.chromeAutofillEnabled }
        }

    @Test
    fun `state carries the stored auto lock timeout`() = runTest(dispatcher) {
        lockInfoRepository.lockInfo = LockInfo(
            autoLockTimeout = LockInfo.Timeout.FIVE_MINUTES,
        )
        val vm = viewModel()

        assertEquals(
            LockInfo.Timeout.FIVE_MINUTES,
            vm.state.first { it.lockTimeout == LockInfo.Timeout.FIVE_MINUTES }.lockTimeout,
        )
    }

    @Test
    fun `selecting an auto lock timeout persists it and updates the ui`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.first()

        vm.onEvent(SettingsUiEvent.SetAutoLockTimeout(LockInfo.Timeout.TWO_MINUTES))

        vm.state.first { it.lockTimeout == LockInfo.Timeout.TWO_MINUTES }
        assertEquals(LockInfo.Timeout.TWO_MINUTES, lockInfoRepository.lockInfo.autoLockTimeout)
    }

    @Test
    fun `opening chrome autofill settings calls the repository directly without emitting an event`() =
        runTest(dispatcher) {
            val vm = viewModel()

            vm.onEvent(SettingsUiEvent.OpenChromeAutofillSettings)

            assertTrue(chromeAutofillRepository.openCalled)
        }
}
