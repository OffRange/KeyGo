package de.davis.keygo.feature.auth.presentation

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import de.davis.keygo.core.ui.model.UiFieldError
import de.davis.keygo.core.identity.FakeAccountRepository
import de.davis.keygo.core.identity.domain.usecase.CreateAccessUseCase
import de.davis.keygo.core.identity.domain.usecase.UnlockWithPasswordUseCase
import de.davis.keygo.core.item.FakeVaultContextRepository
import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.security.crypto.FakeBiometricAvailabilityRepository
import de.davis.keygo.core.security.crypto.FakeSession
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
import de.davis.keygo.rust.FakeAccountManager
import de.davis.keygo.rust.FakeKeyDeriver
import de.davis.keygo.rust.FakeKeyWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val accountRepository = FakeAccountRepository()
    private val vaultRepository = FakeVaultRepository()
    private val vaultContextRepository = FakeVaultContextRepository()
    private val session = FakeSession()
    private val keyDeriver = FakeKeyDeriver()
    private val keyWrapper = FakeKeyWrapper()
    private val accountManager = FakeAccountManager()
    private val biometricAvailability = FakeBiometricAvailabilityRepository()
    private val mainPasswordRepository = FakeMainPasswordRepository()

    private val createAllAccesses = CreateAccessUseCase(
        keyDeriver = keyDeriver,
        keyWrapper = keyWrapper,
        accountManager = accountManager,
        accountRepository = accountRepository,
        vaultRepository = vaultRepository,
        vaultContextRepository = vaultContextRepository,
        session = session,
    )

    private val unlockWithPassword = UnlockWithPasswordUseCase(
        session = session,
        accountRepository = accountRepository,
        keyDeriver = keyDeriver,
        keyWrapper = keyWrapper,
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
        runPendingMigration: RunPendingMigrationUseCase =
            runPendingMigrationUseCase(mainPasswordRepository),
    ): AuthViewModel {
        val vm = AuthViewModel(
            savedStateHandle = SavedStateHandle(),
            biometricAvailabilityRepository = biometricAvailability,
            accountRepository = accountRepository,
            hasV1MainPassword = hasV1MainPassword,
            validateMainPassword = validateMainPassword,
            runPendingMigration = runPendingMigration,
            unlockWithPassword = unlockWithPassword,
            createAllAccesses = createAllAccesses,
        )
        runCurrent()
        return vm
    }

    /**
     * Key derivation inside [CreateAccessUseCase] hops to `Dispatchers.Default`, which the test
     * scheduler can't see, so wait for the loading flag to flip back rather than
     * `advanceUntilIdle()`.
     */
    private suspend fun AuthViewModel.awaitIdle() {
        uiState.first { it is AuthState.Migrating && !it.loading }
    }

    @Test
    fun `failed biometric wrapping leaves the v1 password intact so migration can be retried`() =
        runTest(dispatcher) {
            mainPasswordRepository.hash = "original-v1-hash"
            val vm = viewModel()
            // An uninitialized cipher throws IllegalStateException on wrap(), mirroring a failed
            // biometric crypto operation surfacing as CreateAccessError.WrappingFailed - the exact
            // failure mode described in the bug report.
            val failingCipher = Cipher.getInstance("AES/GCM/NoPadding")

            vm.executeCreateAccess(password = "correct-password", cipher = failingCipher)
            vm.awaitIdle()

            assertEquals("original-v1-hash", mainPasswordRepository.hash)
        }

    @Test
    fun `successful biometric account creation clears the v1 password`() = runTest(dispatcher) {
        mainPasswordRepository.hash = "original-v1-hash"
        val vm = viewModel()
        val biometricKek = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.WRAP_MODE, biometricKek)
        }

        vm.executeCreateAccess(password = "correct-password", cipher = cipher)
        vm.navigationEvent.first()

        assertEquals("", mainPasswordRepository.hash)
    }

    @Test
    fun `account persistence failure on the password-only path leaves the v1 password intact`() =
        runTest(dispatcher) {
            mainPasswordRepository.hash = "original-v1-hash"
            accountRepository.setFails = true
            val vm = viewModel()

            vm.executeCreateAccess(password = "correct-password")
            vm.awaitIdle()

            assertEquals("original-v1-hash", mainPasswordRepository.hash)
        }

    @Test
    fun `successful password-only account creation clears the v1 password`() = runTest(dispatcher) {
        mainPasswordRepository.hash = "original-v1-hash"
        val vm = viewModel()

        vm.executeCreateAccess(password = "correct-password")
        vm.navigationEvent.first()

        assertEquals("", mainPasswordRepository.hash)
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
            mainPasswordRepository.hash = "original-v1-hash"
            val vm = viewModel()

            vm.executeCreateAccess(password = "correct-password")
            // Leaves the first run suspended inside key derivation, which hops to a dispatcher the
            // scheduler cannot see, so it cannot complete until something pumps the test one.
            runCurrent()
            vm.executeCreateAccess(password = "correct-password")

            vm.navigationEvent.first()

            assertEquals(1, accountRepository.setCount)
        }

    /**
     * The Migrating submit path reports a rejected password through the loading scope rather than
     * writing to `uiState` itself. `loading` writes the scope's state back once the block returns,
     * so a direct write lands first and is then overwritten, stopping the spinner and leaving the
     * field with nothing on it.
     *
     * The only test that drives the real `Submit` event, so it is also the only one that needs a
     * hash `ValidateMainPasswordUseCase` can actually decode. The other tests seed a placeholder
     * and reach the ViewModel past validation.
     */
    @Test
    fun `a rejected v1 main password leaves an error on the field`() = runTest(dispatcher) {
        // Hex of a real bcrypt 2a hash of "password". The use case hex-decodes the stored hash
        // before handing it to bcrypt, so a non-hex placeholder throws instead of returning false.
        mainPasswordRepository.hash = "243261243130244e39716f38754c4f69636b6778325a4d525a6f4d7965" +
                "496a5a416763666c377039326c644778616436384c4a5a644c31376c685779"
        val vm = viewModel()

        val migrating = assertIs<AuthState.Migrating>(vm.uiState.value)
        migrating.passwordTextFieldState.setTextAndPlaceCursorAtEnd("the-wrong-password")

        vm.onEvent(AuthUIEvent.Submit)
        // Bcrypt runs on Dispatchers.Default, which the scheduler cannot see, so wait on the
        // loading flag for the same reason awaitIdle does.
        vm.awaitIdle()

        val after = assertIs<AuthState.Migrating>(vm.uiState.value)
        assertEquals(UiFieldError.Incorrect, after.passwordError)
    }

    /**
     * The descendant of the 97b15f3c regression test. There the v1 password was dropped before the
     * account existed. Here it must not be dropped before the user's rows are across.
     */
    @Test
    fun `a failed import leaves the v1 password in place and offers a retry`() =
        runTest(dispatcher) {
            mainPasswordRepository.hash = "original-v1-hash"
            val vm = viewModel(
                runPendingMigration = runPendingMigrationUseCase(
                    repository = mainPasswordRepository,
                    outcome = LegacyMigrationOutcome.Failed(IllegalStateException("unreadable")),
                ),
            )

            vm.executeCreateAccess(password = "correct-password")
            vm.uiState.first { it is AuthState.MigrationFailed }

            assertEquals("original-v1-hash", mainPasswordRepository.hash)
        }

    @Test
    fun `retrying a failed import runs it again`() = runTest(dispatcher) {
        mainPasswordRepository.hash = "original-v1-hash"
        var runs = 0
        var stateDuringImport: AuthState? = null
        // Sampled from inside the import because uiState is conflated: ImportingLegacyData is
        // replaced before any collector is resumed, so this is the only place it can be observed.
        var underTest: AuthViewModel? = null
        val vm = viewModel(
            runPendingMigration = runPendingMigrationUseCase(
                repository = mainPasswordRepository,
                outcome = LegacyMigrationOutcome.Failed(IllegalStateException("unreadable")),
                onImport = {
                    runs++
                    stateDuringImport = underTest?.uiState?.value
                },
            ),
        )
        underTest = vm

        vm.executeCreateAccess(password = "correct-password")
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
        assertEquals("original-v1-hash", mainPasswordRepository.hash)
    }

    @Test
    fun `an import that skipped rows reports them before navigating`() = runTest(dispatcher) {
        mainPasswordRepository.hash = "original-v1-hash"
        val vm = viewModel(
            runPendingMigration = runPendingMigrationUseCase(
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

        vm.executeCreateAccess(password = "correct-password")
        val state = vm.uiState.first { it is AuthState.MigrationSummary }

        assertEquals(2, (state as AuthState.MigrationSummary).skippedItems)
        assertEquals("", mainPasswordRepository.hash)
    }
}
