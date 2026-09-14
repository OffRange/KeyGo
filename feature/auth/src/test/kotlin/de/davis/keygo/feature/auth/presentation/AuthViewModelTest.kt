package de.davis.keygo.feature.auth.presentation

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import de.davis.keygo.core.biometrics.FakeBiometricAvailabilityRepository
import de.davis.keygo.core.biometrics.FakeBiometricCrypto
import de.davis.keygo.core.biometrics.domain.model.BiometricAuthError
import de.davis.keygo.core.identity.FakeAccountRepository
import de.davis.keygo.core.identity.domain.model.Account
import de.davis.keygo.core.identity.domain.usecase.CreateAccessUseCase
import de.davis.keygo.core.identity.domain.usecase.DisableBiometricsUseCase
import de.davis.keygo.core.identity.domain.usecase.UnlockWithBiometricsUseCase
import de.davis.keygo.core.identity.domain.usecase.UnlockWithPasswordUseCase
import de.davis.keygo.core.identity.domain.usecase.UnlockableByBiometricsUseCase
import de.davis.keygo.core.item.FakeVaultContextRepository
import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.security.FakeSession
import de.davis.keygo.core.security.crypto.FakeKeyStoreManager
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.ui.model.UiFieldError
import de.davis.keygo.core.util.isSuccess
import de.davis.keygo.feature.auth.presentation.model.AuthState
import de.davis.keygo.feature.auth.presentation.model.AuthUIEvent
import de.davis.keygo.legacy_migration.FakeMainPasswordRepository
import de.davis.keygo.legacy_migration.domain.model.LegacyFailureReason
import de.davis.keygo.legacy_migration.domain.model.LegacyMigrationOutcome
import de.davis.keygo.legacy_migration.domain.model.LegacyMigrationReport
import de.davis.keygo.legacy_migration.domain.model.LegacyRowFailure
import de.davis.keygo.legacy_migration.domain.usecase.RunPendingMigrationUseCase
import de.davis.keygo.legacy_migration.hasMainPasswordUseCase
import de.davis.keygo.legacy_migration.runPendingMigrationUseCase
import de.davis.keygo.legacy_migration.validateMainPasswordUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

/**
 * Regression tests for the v1-password retry lockout fixed in `97b15f3c`.
 *
 * [AuthViewModel.executeCreateAccess] used to clear the v1 migration password as soon as the
 * password was validated, before the account was actually created. If account creation then
 * failed for any reason - most notably a failed/declined biometric prompt - the v1 password was
 * already gone, so `HasMainPasswordUseCase` reported no pending migration and the user had no way
 * to retry. Clearing the marker now belongs to [RunPendingMigrationUseCase], which runs only after
 * a session exists and only once the import has said something definite about the v1 file.
 */
@RunWith(RobolectricTestRunner::class) // Robolectric for android.util.Log alone.
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val accountRepository = FakeAccountRepository()
    private val vaultRepository = FakeVaultRepository()
    private val vaultContextRepository = FakeVaultContextRepository()
    private val session = FakeSession()
    private val keyStoreManager = FakeKeyStoreManager()
    private val biometricCrypto = FakeBiometricCrypto(keyStoreManager)
    private val mainPasswordRepository = FakeMainPasswordRepository()

    private val biometricAvailability = FakeBiometricAvailabilityRepository().apply {
        isAvailable = true
    }

    private val createAllAccesses = CreateAccessUseCase(
        accountRepository = accountRepository,
        vaultRepository = vaultRepository,
        vaultContextRepository = vaultContextRepository,
        biometricCrypto = biometricCrypto,
        session = session,
    )

    private val unlockWithPassword = UnlockWithPasswordUseCase(
        session = session,
        accountRepository = accountRepository,
    )

    private val unlockWithBiometrics = UnlockWithBiometricsUseCase(
        session = session,
        accountRepository = accountRepository,
        biometricCrypto = biometricCrypto,
        disableBiometrics = DisableBiometricsUseCase(accountRepository, keyStoreManager),
    )

    private val unlockableByBiometrics = UnlockableByBiometricsUseCase(
        accountRepository = accountRepository,
        biometricAvailabilityRepository = biometricAvailability,
    )

    // Real use cases, wired to mainPasswordRepository via factories - HasMainPasswordUseCase and
    // ValidateMainPasswordUseCase have an internal constructor scoped to the migration module, so
    // they can't be built here directly the way a plain fake dependency would be.
    private val hasV1MainPassword = hasMainPasswordUseCase(mainPasswordRepository)
    private val validateMainPassword = validateMainPasswordUseCase(mainPasswordRepository)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /**
     * Builds the ViewModel and lets its `init` block settle. `hasV1MainPassword` reflects
     * [mainPasswordRepository]'s state, so seed a hash before calling this for the ViewModel to
     * resolve into `AuthState.Migrating`.
     */
    private fun TestScope.viewModel(
        authRoute: AuthRoute = AuthRoute(),
        runPendingMigration: RunPendingMigrationUseCase =
            runPendingMigrationUseCase(backgroundScope, mainPasswordRepository),
    ): AuthViewModel {
        val vm = AuthViewModel(
            authRoute = authRoute,
            unlockableByBiometrics = unlockableByBiometrics,
            hasV1MainPassword = hasV1MainPassword,
            validateMainPassword = validateMainPassword,
            runPendingMigration = runPendingMigration,
            unlockWithBiometrics = unlockWithBiometrics,
            unlockWithPassword = unlockWithPassword,
            createAllAccesses = createAllAccesses,
        )
        runCurrent()
        return vm
    }

    private fun TestScope.loginViewModel(
        runPendingMigration: RunPendingMigrationUseCase =
            runPendingMigrationUseCase(backgroundScope, mainPasswordRepository),
    ) = viewModel(
        authRoute = AuthRoute(showBiometricPromptIfPossible = false),
        runPendingMigration = runPendingMigration,
    )

    /**
     * Key derivation inside [CreateAccessUseCase] hops to `Dispatchers.Default`, which the test
     * scheduler can't see, so wait for the loading flag to flip back rather than
     * `advanceUntilIdle()`.
     */
    private suspend fun AuthViewModel.awaitIdle() {
        uiState.first { it is AuthState.Migrating && !it.loading }
    }

    private fun AuthViewModel.submitMigration(
        password: String = V1_PASSWORD,
        useBiometrics: Boolean = false,
    ) {
        onEvent(AuthUIEvent.ToggleUseBiometrics(checked = useBiometrics))
        assertIs<AuthState.Migrating>(uiState.value)
            .passwordTextFieldState
            .setTextAndPlaceCursorAtEnd(password)
        onEvent(AuthUIEvent.Submit)
    }

    /** An account whose vault can be opened by the biometric key, so the button is offered. */
    private suspend fun seedEnrolledAccount(withBiometrics: Boolean = true) {
        assertTrue(createAllAccesses(ACCOUNT_PASSWORD, withBiometrics = withBiometrics).isSuccess())
        session.endSession()
        biometricCrypto.prompts.clear()
    }

    @Test
    fun `an enrolled account opens the prompt on arrival and continues once unlocked`() =
        runTest(dispatcher) {
            seedEnrolledAccount()

            val vm = viewModel()
            vm.navigationEvent.first()

            assertTrue(session.isActive.value)
            assertEquals(CryptographicMode.Unwrap, biometricCrypto.prompts.single().mode)
        }

    @Test
    fun `the prompt waits for the user when the route asks it to`() = runTest(dispatcher) {
        seedEnrolledAccount()

        val vm = loginViewModel()

        assertEquals(
            true,
            assertIs<AuthState.Login>(vm.uiState.value).biometricAuthenticationAvailable,
        )
        assertTrue(biometricCrypto.prompts.isEmpty())
        assertFalse(session.isActive.value)
    }

    @Test
    fun `asking for biometrics unlocks and continues`() = runTest(dispatcher) {
        seedEnrolledAccount()
        val vm = loginViewModel()

        vm.onEvent(AuthUIEvent.RequestBiometricAuthentication)
        vm.navigationEvent.first()

        assertTrue(session.isActive.value)
    }

    @Test
    fun `biometrics are neither offered nor prompted without a usable sensor`() =
        runTest(dispatcher) {
            seedEnrolledAccount()
            biometricAvailability.isAvailable = false

            val vm = viewModel()

            assertEquals(
                false,
                assertIs<AuthState.Login>(vm.uiState.value).biometricAuthenticationAvailable,
            )
            assertTrue(biometricCrypto.prompts.isEmpty())
        }

    @Test
    fun `biometrics are neither offered nor prompted for an account that never enrolled`() =
        runTest(dispatcher) {
            seedEnrolledAccount(withBiometrics = false)

            val vm = viewModel()

            assertEquals(
                false,
                assertIs<AuthState.Login>(vm.uiState.value).biometricAuthenticationAvailable,
            )
            assertTrue(biometricCrypto.prompts.isEmpty())
        }

    @Test
    fun `a declined prompt leaves the login form as it was`() = runTest(dispatcher) {
        seedEnrolledAccount()
        biometricCrypto.promptFailure = BiometricAuthError.Declined
        val vm = loginViewModel()

        vm.onEvent(AuthUIEvent.RequestBiometricAuthentication)
        runCurrent()

        val login = assertIs<AuthState.Login>(vm.uiState.value)
        assertEquals(true, login.biometricAuthenticationAvailable)
        assertEquals(false, login.showBiometricResetNotice)
        assertFalse(session.isActive.value)
    }

    /**
     * The unlock adapter deletes the stored enrollment on its own when the key behind it is gone.
     * Hiding the button is all the screen would otherwise do about it, which reads as a security
     * setting vanishing for no reason, so the drop is said out loud.
     */
    @Test
    fun `a reset enrollment is announced rather than quietly disappearing`() = runTest(dispatcher) {
        seedEnrolledAccount()
        keyStoreManager.deleteKey(KeyId.BiometricVaultKek)
        val vm = loginViewModel()
        assertEquals(
            true,
            assertIs<AuthState.Login>(vm.uiState.value).biometricAuthenticationAvailable,
        )

        vm.onEvent(AuthUIEvent.RequestBiometricAuthentication)
        runCurrent()

        val login = assertIs<AuthState.Login>(vm.uiState.value)
        assertEquals(false, login.biometricAuthenticationAvailable)
        assertEquals(true, login.showBiometricResetNotice)
        assertNull(accountRepository.getOrNull()?.biometricWrappedArk)
    }

    @Test
    fun `dismissing the notice leaves the enrollment gone`() = runTest(dispatcher) {
        seedEnrolledAccount()
        keyStoreManager.deleteKey(KeyId.BiometricVaultKek)
        val vm = loginViewModel()
        vm.onEvent(AuthUIEvent.RequestBiometricAuthentication)
        runCurrent()

        vm.onEvent(AuthUIEvent.DismissBiometricResetNotice)

        val login = assertIs<AuthState.Login>(vm.uiState.value)
        assertEquals(false, login.showBiometricResetNotice)
        assertEquals(false, login.biometricAuthenticationAvailable)
    }

    @Test
    fun `a retryable biometric failure announces nothing and keeps the button`() =
        runTest(dispatcher) {
            seedEnrolledAccount()
            biometricCrypto.promptFailure = BiometricAuthError.CryptoFailed
            val vm = loginViewModel()

            vm.onEvent(AuthUIEvent.RequestBiometricAuthentication)
            runCurrent()

            val login = assertIs<AuthState.Login>(vm.uiState.value)
            assertEquals(false, login.showBiometricResetNotice)
            assertEquals(true, login.biometricAuthenticationAvailable)
            assertNotNull(accountRepository.getOrNull()?.biometricWrappedArk)
        }

    @Test
    fun `a pending migration offers biometrics when the sensor is usable`() = runTest(dispatcher) {
        mainPasswordRepository.hash = V1_HASH

        val vm = viewModel()

        assertEquals(true, assertIs<AuthState.Migrating>(vm.uiState.value).biometricsAvailable)
    }

    @Test
    fun `a pending migration is offered on a device without usable biometrics`() =
        runTest(dispatcher) {
            biometricAvailability.isAvailable = false
            mainPasswordRepository.hash = V1_HASH

            val vm = viewModel()

            val migrating = assertIs<AuthState.Migrating>(vm.uiState.value)
            assertEquals(false, migrating.biometricsAvailable)
        }

    @Test
    fun `failed biometric wrapping leaves the v1 password intact so migration can be retried`() =
        runTest(dispatcher) {
            mainPasswordRepository.hash = V1_HASH
            biometricCrypto.promptFailure = BiometricAuthError.Declined
            val vm = viewModel()

            vm.submitMigration(useBiometrics = true)
            vm.awaitIdle()

            assertEquals(V1_HASH, mainPasswordRepository.hash)
            assertNull(accountRepository.getOrNull())
        }

    @Test
    fun `successful biometric account creation enrolls and clears the v1 password`() =
        runTest(dispatcher) {
            mainPasswordRepository.hash = V1_HASH
            val vm = viewModel()

            vm.submitMigration(useBiometrics = true)
            vm.navigationEvent.first()

            assertEquals("", mainPasswordRepository.hash)
            assertNotNull(accountRepository.getOrNull()?.biometricWrappedArk)
            assertEquals(CryptographicMode.Wrap, biometricCrypto.prompts.single().mode)
        }

    @Test
    fun `migrating with biometrics switched off creates a password-only account`() =
        runTest(dispatcher) {
            mainPasswordRepository.hash = V1_HASH
            val vm = viewModel()

            vm.submitMigration(useBiometrics = false)
            vm.navigationEvent.first()

            assertNull(accountRepository.getOrNull()?.biometricWrappedArk)
            assertTrue(biometricCrypto.prompts.isEmpty())
        }

    @Test
    fun `account persistence failure on the password-only path leaves the v1 password intact`() =
        runTest(dispatcher) {
            mainPasswordRepository.hash = V1_HASH
            accountRepository.setFails = true
            val vm = viewModel()

            vm.submitMigration()
            vm.awaitIdle()

            assertEquals(V1_HASH, mainPasswordRepository.hash)
        }

    @Test
    fun `successful password-only account creation clears the v1 password`() = runTest(dispatcher) {
        mainPasswordRepository.hash = V1_HASH
        val vm = viewModel()

        vm.submitMigration()
        vm.navigationEvent.first()

        assertEquals("", mainPasswordRepository.hash)
    }

    @Test
    fun `the migrate submit stays loading until the account exists`() = runTest(dispatcher) {
        mainPasswordRepository.hash = V1_HASH
        val vm = viewModel()

        vm.submitMigration()
        runCurrent()

        // Key derivation is still running on a dispatcher the scheduler cannot see. The screen must
        // not have handed control back yet.
        assertEquals(true, assertIs<AuthState.Migrating>(vm.uiState.value).loading)

        vm.navigationEvent.first()

        assertEquals(1, accountRepository.setCount)
    }

    /**
     * Key derivation takes long enough for a second tap to land inside it. `onEvent` gates on the
     * state being interactable rather than on the loading flag, so before the guard the only thing
     * stopping a second run was the button's own enabled state, and the migrate path stopped the
     * spinner while derivation was still going. Two runs mint two accounts, two ARKs and two
     * vaults, and the second overwrites the registry, leaving the first vault wrapped under an ARK
     * nothing persists.
     */
    @Test
    fun `a second account creation started while one is in flight is dropped`() =
        runTest(dispatcher) {
            mainPasswordRepository.hash = V1_HASH
            val vm = viewModel()

            vm.submitMigration()
            // Leaves the first run suspended inside key derivation, which hops to a dispatcher the
            // scheduler cannot see, so it cannot complete until something pumps the test one.
            runCurrent()
            vm.onEvent(AuthUIEvent.Submit)

            vm.navigationEvent.first()

            assertEquals(1, accountRepository.setCount)
        }

    @Test
    fun `a rejected v1 main password leaves an error on the field`() = runTest(dispatcher) {
        mainPasswordRepository.hash = V1_HASH
        val vm = viewModel()

        vm.submitMigration(password = "the-wrong-password")
        // Bcrypt runs on Dispatchers.Default, which the scheduler cannot see, so wait on the
        // loading flag for the same reason awaitIdle does.
        vm.awaitIdle()

        val after = assertIs<AuthState.Migrating>(vm.uiState.value)
        assertEquals(UiFieldError.Incorrect, after.passwordError)
        assertNull(accountRepository.getOrNull())
    }

    @Test
    fun `a failed import leaves the v1 password in place and offers a retry`() =
        runTest(dispatcher) {
            mainPasswordRepository.hash = V1_HASH
            val vm = viewModel(
                runPendingMigration = runPendingMigrationUseCase(
                    scope = backgroundScope,
                    repository = mainPasswordRepository,
                    outcome = LegacyMigrationOutcome.Failed(IllegalStateException("unreadable")),
                ),
            )

            vm.submitMigration()
            vm.uiState.first { it is AuthState.MigrationFailed }

            assertEquals(V1_HASH, mainPasswordRepository.hash)
        }

    @Test
    fun `retrying a failed import runs it again`() = runTest(dispatcher) {
        mainPasswordRepository.hash = V1_HASH
        var runs = 0
        var stateDuringImport: AuthState? = null
        // Sampled from inside the import because uiState is conflated: ImportingLegacyData is
        // replaced before any collector is resumed, so this is the only place it can be observed.
        var underTest: AuthViewModel? = null
        val vm = viewModel(
            runPendingMigration = runPendingMigrationUseCase(
                scope = backgroundScope,
                repository = mainPasswordRepository,
                outcome = LegacyMigrationOutcome.Failed(IllegalStateException("unreadable")),
                onImport = {
                    runs++
                    stateDuringImport = underTest?.uiState?.value
                },
            ),
        )
        underTest = vm

        vm.submitMigration()
        vm.uiState.first { it is AuthState.MigrationFailed }
        assertEquals(1, runs)
        assertEquals(AuthState.ImportingLegacyData, stateDuringImport)

        // The retry passes through ImportingLegacyData and back to MigrationFailed without ever
        // suspending, because the import is a fake. uiState is conflated, so a collector waiting on
        // the intermediate state is only ever resumed after the final one has replaced it and would
        // wait forever. Drain the scheduler instead and assert on what the retry left behind.
        vm.onEvent(AuthUIEvent.RetryMigration)
        runCurrent()

        assertEquals(2, runs)
        assertIs<AuthState.MigrationFailed>(vm.uiState.value)
        assertEquals(V1_HASH, mainPasswordRepository.hash)
    }

    /**
     * The write that ends a loading run puts back a snapshot taken before `block()` suspended, so
     * it has to be guarded the same way the one that starts the run is.
     *
     * `onSessionEstablished` is reachable from outside `loading`: AuthScreen calls it straight from
     * the BiometricRequest.Login success handler and nothing gates it on `authJob`. So the user
     * submits the password form, the biometric prompt they already triggered comes back, the import
     * starts, and an unguarded write here would drop the live login form back on top of it - a form
     * they can submit again while the import runs behind it.
     */
    @Test
    fun `a state set while the auth run was suspended is not written over`() = runTest(dispatcher) {
        // An account plus a marker still on disk: what the user is left with the moment they tap
        // Continue on MigrationFailed. Every unlock after that is a login form over a migration
        // that is still pending.
        seedEnrolledAccount()
        mainPasswordRepository.hash = V1_HASH

        val vm = loginViewModel(
            runPendingMigration = runPendingMigrationUseCase(
                scope = backgroundScope,
                repository = mainPasswordRepository,
                outcome = LegacyMigrationOutcome.Failed(IllegalStateException("unreadable")),
            ),
        )
        val login = assertIs<AuthState.Login>(vm.uiState.value)
        login.passwordTextFieldState.setTextAndPlaceCursorAtEnd(ACCOUNT_PASSWORD)

        val prompt = CompletableDeferred<Unit>()
        biometricCrypto.pendingPrompt = prompt
        vm.onEvent(AuthUIEvent.RequestBiometricAuthentication)
        runCurrent()

        // Holds the unlock at its account read, which is the last point before it hops to a
        // dispatcher the scheduler cannot see.
        val read = CompletableDeferred<Account?>()
        accountRepository.pendingRead = read
        vm.onEvent(AuthUIEvent.Submit)
        runCurrent()
        assertEquals(true, assertIs<AuthState.Login>(vm.uiState.value).loading)

        prompt.complete(Unit)
        runCurrent()
        assertIs<AuthState.MigrationFailed>(vm.uiState.value)

        // Answering with no account fails the unlock where it stands, so the only thing left to
        // happen is loading writing its snapshot back.
        read.complete(null)
        runCurrent()

        assertIs<AuthState.MigrationFailed>(vm.uiState.value)
    }

    @Test
    fun `an import that skipped rows reports them before navigating`() = runTest(dispatcher) {
        mainPasswordRepository.hash = V1_HASH
        val vm = viewModel(
            runPendingMigration = runPendingMigrationUseCase(
                scope = backgroundScope,
                repository = mainPasswordRepository,
                outcome = LegacyMigrationOutcome.Migrated(
                    LegacyMigrationReport(
                        migratedItems = 14,
                        failures = listOf(
                            LegacyRowFailure(1, "an account", LegacyFailureReason.Unreadable),
                            LegacyRowFailure(2, "another", LegacyFailureReason.Unreadable),
                        ),
                    ),
                ),
            ),
        )

        vm.submitMigration()
        val state = vm.uiState.first { it is AuthState.MigrationSummary }

        assertEquals(2, (state as AuthState.MigrationSummary).skippedItems)
        assertEquals("", mainPasswordRepository.hash)
    }

    private companion object {
        const val ACCOUNT_PASSWORD = "correct-password"

        const val V1_PASSWORD = "password"

        // Hex of a real bcrypt 2a hash of "password". The use case hex-decodes before verifying.
        const val V1_HASH = "2432612431302471776e45776767315a6c5176435a58336450614a7a2e" +
                "31494351504a334e6d4a64566b4251686577564655745363646665366d4847"
    }
}
