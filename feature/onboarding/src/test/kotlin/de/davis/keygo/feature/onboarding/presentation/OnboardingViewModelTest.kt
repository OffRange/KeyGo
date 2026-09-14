package de.davis.keygo.feature.onboarding.presentation

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import de.davis.keygo.core.biometrics.FakeBiometricAvailabilityRepository
import de.davis.keygo.core.biometrics.FakeBiometricCrypto
import de.davis.keygo.core.biometrics.domain.model.BiometricAuthError
import de.davis.keygo.core.feature.autofill.FakeAutofillServiceRepository
import de.davis.keygo.core.feature.autofill.FakeChromeAutofillRepository
import de.davis.keygo.core.identity.FakeAccountRepository
import de.davis.keygo.core.identity.domain.usecase.CreateAccessUseCase
import de.davis.keygo.core.identity.domain.usecase.EnableBiometricsUseCase
import de.davis.keygo.core.item.FakeVaultContextRepository
import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.item.domain.estimator.PasswordStrengthEstimator
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.security.FakeSession
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.feature.autofill.domain.usecase.AutofillActivationStatusUseCase
import de.davis.keygo.feature.onboarding.presentation.model.OnboardingUiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val accountRepository = FakeAccountRepository()
    private val session = FakeSession()
    private val biometricCrypto = FakeBiometricCrypto()
    private val autofillServiceRepository = FakeAutofillServiceRepository()
    private val chromeAutofillRepository = FakeChromeAutofillRepository()

    private val biometricAvailability = FakeBiometricAvailabilityRepository().apply {
        isAvailable = true
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel() = OnboardingViewModel(
        onboardingRoute = OnboardingRoute(),
        biometricAvailabilityRepository = biometricAvailability,
        chromeAutofillRepository = chromeAutofillRepository,
        autofillActivationStatus = AutofillActivationStatusUseCase(
            autofillServiceRepository = autofillServiceRepository,
            chromeAutofillRepository = chromeAutofillRepository,
        ),
        passwordStrengthEstimator = object : PasswordStrengthEstimator {
            override suspend fun estimate(password: String): PasswordScore = PasswordScore.None
        },
        createAccess = CreateAccessUseCase(
            accountRepository = accountRepository,
            vaultRepository = FakeVaultRepository(),
            vaultContextRepository = FakeVaultContextRepository(),
            enableBiometrics = EnableBiometricsUseCase(
                accountRepository = accountRepository,
                session = session,
                keyStoreManager = biometricCrypto.keyStoreManager,
                biometricCrypto = biometricCrypto,
            ),
            session = session,
        ),
    ).also {
        it.state.launchIn(backgroundScope)
        runCurrent()
    }

    private suspend fun TestScope.enterMainPassword(vm: OnboardingViewModel) {
        vm.onNextStep()
        runCurrent()
        val form = vm.state.first { it is OnboardingUiState.SetMainPassword }
                as OnboardingUiState.SetMainPassword
        form.passwordTextFieldState.setTextAndPlaceCursorAtEnd(PASSWORD)
        form.confirmPasswordTextFieldState.setTextAndPlaceCursorAtEnd(PASSWORD)
    }

    private suspend fun awaitStepAfterPasswordForm(vm: OnboardingViewModel): OnboardingUiState =
        vm.state.first { it !is OnboardingUiState.SetMainPassword }

    private suspend fun TestScope.reachBiometricsStep(vm: OnboardingViewModel) {
        enterMainPassword(vm)
        vm.onNextStep()
        assertEquals(OnboardingUiState.EnableBiometrics, awaitStepAfterPasswordForm(vm))
    }

    @Test
    fun `enabling biometrics creates an account enrolled for them and moves on`() =
        runTest(dispatcher) {
            val vm = viewModel()
            reachBiometricsStep(vm)

            vm.onNextStep()
            advanceUntilIdle()

            assertIs<OnboardingUiState.ImportData>(vm.state.value)
            assertNotNull(accountRepository.getOrNull()?.biometricWrappedArk)
            assertEquals(CryptographicMode.Wrap, biometricCrypto.prompts.single().mode)
            assertTrue(session.isActive.value)
        }

    @Test
    fun `skipping biometrics creates a password-only account without prompting`() =
        runTest(dispatcher) {
            val vm = viewModel()
            reachBiometricsStep(vm)

            vm.onSkip()
            advanceUntilIdle()

            assertIs<OnboardingUiState.ImportData>(vm.state.value)
            val account = assertNotNull(accountRepository.getOrNull())
            assertNull(account.biometricWrappedArk)
            assertTrue(biometricCrypto.prompts.isEmpty())
        }

    /**
     * Biometrics are optional on top of the password the user just chose. Before, a cancelled prompt
     * threw the finished key derivation away and left the user on this step with no feedback.
     */
    @Test
    fun `a failed prompt still creates a password-only account and moves on`() =
        runTest(dispatcher) {
            biometricCrypto.promptFailure = BiometricAuthError.Declined
            val vm = viewModel()
            reachBiometricsStep(vm)

            vm.onNextStep()
            advanceUntilIdle()

            assertIs<OnboardingUiState.ImportData>(vm.state.value)
            assertNull(assertNotNull(accountRepository.getOrNull()).biometricWrappedArk)
            assertFalse(vm.loading.value)
            assertTrue(session.isActive.value)
        }

    @Test
    fun `a second tap while the account is being created is dropped`() = runTest(dispatcher) {
        val vm = viewModel()
        reachBiometricsStep(vm)

        vm.onSkip()
        vm.onSkip()
        advanceUntilIdle()

        assertEquals(1, accountRepository.setCount)
    }

    @Test
    fun `the biometrics step reads as loading while the prompt is open`() = runTest(dispatcher) {
        val prompt = CompletableDeferred<Unit>()
        biometricCrypto.pendingPrompt = prompt
        val vm = viewModel()
        reachBiometricsStep(vm)

        vm.onNextStep()
        runCurrent()
        assertTrue(vm.loading.value)
        assertEquals(OnboardingUiState.EnableBiometrics, vm.state.value)

        prompt.complete(Unit)
        advanceUntilIdle()

        assertFalse(vm.loading.value)
        assertIs<OnboardingUiState.ImportData>(vm.state.value)
    }

    @Test
    fun `without usable biometrics the account is created straight from the password step`() =
        runTest(dispatcher) {
            biometricAvailability.isAvailable = false
            val vm = viewModel()
            enterMainPassword(vm)

            vm.onNextStep()
            advanceUntilIdle()

            val account = assertNotNull(accountRepository.getOrNull())
            assertIs<OnboardingUiState.ImportData>(awaitStepAfterPasswordForm(vm))
            assertNull(account.biometricWrappedArk)
            assertTrue(biometricCrypto.prompts.isEmpty())
        }

    private companion object {
        const val PASSWORD = "correct horse battery staple"
    }
}
