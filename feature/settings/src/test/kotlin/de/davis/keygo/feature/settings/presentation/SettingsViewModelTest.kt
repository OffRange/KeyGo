package de.davis.keygo.feature.settings.presentation

import de.davis.keygo.core.feature.autofill.FakeAutofillServiceRepository
import de.davis.keygo.core.feature.settings.FakeAppVersionRepository
import de.davis.keygo.core.identity.FakeAccountRepository
import de.davis.keygo.core.security.crypto.FakeBiometricAvailabilityRepository
import de.davis.keygo.feature.backup.domain.model.LastBackup
import de.davis.keygo.feature.backup.domain.usecase.ObserveLastBackupUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val accountRepository = FakeAccountRepository()
    private val biometricAvailability = FakeBiometricAvailabilityRepository()
    private val autofillServiceRepository = FakeAutofillServiceRepository()
    private val appVersionRepository = FakeAppVersionRepository()
    private val lastBackup = MutableStateFlow<LastBackup?>(null)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = SettingsViewModel(
        biometricAvailabilityRepository = biometricAvailability,
        autofillServiceRepository = autofillServiceRepository,
        accountRepository = accountRepository,
        appVersionRepository = appVersionRepository,
        observeLastBackup = ObserveLastBackupUseCase { lastBackup },
    )

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
    fun `toggling biometrics forwards the requested value as an EnableBiometric event`() =
        runTest(dispatcher) {
            val vm = viewModel()

            vm.onEvent(SettingsUiEvent.SetBiometrics(enabled = true))

            assertEquals(SettingsEvent.EnableBiometric(enable = true), vm.event.first())
        }

    @Test
    fun `opening backup emits NavigateToBackup`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onEvent(SettingsUiEvent.OpenBackup)

        assertEquals(SettingsEvent.NavigateToBackup, vm.event.first())
    }

    @Test
    fun `state carries the newest successful backup timestamp`() = runTest(dispatcher) {
        lastBackup.value = LastBackup(finishedAt = 1_700_000_000_000L)
        val vm = viewModel()

        assertEquals(1_700_000_000_000L, vm.state.first { it.lastBackupAt != null }.lastBackupAt)
    }

    @Test
    fun `state reports no last backup while none has completed`() = runTest(dispatcher) {
        val vm = viewModel()

        assertNull(vm.state.first().lastBackupAt)
    }
}
