package de.davis.keygo.feature.auth.presentation

import androidx.lifecycle.SavedStateHandle
import de.davis.keygo.core.identity.FakeAccountRepository
import de.davis.keygo.core.identity.domain.usecase.CreateAccessUseCase
import de.davis.keygo.core.identity.domain.usecase.UnlockWithPasswordUseCase
import de.davis.keygo.core.item.FakeVaultContextRepository
import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.security.crypto.FakeBiometricAvailabilityRepository
import de.davis.keygo.core.security.crypto.FakeSession
import de.davis.keygo.feature.auth.presentation.model.AuthState
import de.davis.keygo.legacy_migration.FakeMainPasswordRepository
import de.davis.keygo.legacy_migration.clearMainPasswordUseCase
import de.davis.keygo.legacy_migration.hasMainPasswordUseCase
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

/**
 * Regression tests for the v1-password retry lockout fixed in `97b15f3c`.
 *
 * [AuthViewModel.executeCreateAccessAndClearV1] used to clear the v1 migration password as soon
 * as the password was validated, before the account was actually created. If account creation
 * then failed for any reason - most notably a failed/declined biometric prompt - the v1 password
 * was already gone, so `HasMainPasswordUseCase` reported no pending migration and the user had no
 * way to retry. The fix defers `clearMainPasswordUseCase()` until account creation succeeds.
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
    private val clearMainPassword = clearMainPasswordUseCase(mainPasswordRepository)

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
    private fun TestScope.viewModel(): AuthViewModel {
        val vm = AuthViewModel(
            savedStateHandle = SavedStateHandle(),
            biometricAvailabilityRepository = biometricAvailability,
            accountRepository = accountRepository,
            hasV1MainPassword = hasV1MainPassword,
            validateMainPassword = validateMainPassword,
            clearMainPasswordUseCase = clearMainPassword,
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

            vm.executeCreateAccessAndClearV1(password = "correct-password", cipher = failingCipher)
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

        vm.executeCreateAccessAndClearV1(password = "correct-password", cipher = cipher)
        vm.awaitIdle()

        assertEquals("", mainPasswordRepository.hash)
    }

    @Test
    fun `account persistence failure on the password-only path leaves the v1 password intact`() =
        runTest(dispatcher) {
            mainPasswordRepository.hash = "original-v1-hash"
            accountRepository.setFails = true
            val vm = viewModel()

            vm.executeCreateAccessAndClearV1(password = "correct-password")
            vm.awaitIdle()

            assertEquals("original-v1-hash", mainPasswordRepository.hash)
        }

    @Test
    fun `successful password-only account creation clears the v1 password`() = runTest(dispatcher) {
        mainPasswordRepository.hash = "original-v1-hash"
        val vm = viewModel()

        vm.executeCreateAccessAndClearV1(password = "correct-password")
        vm.awaitIdle()

        assertEquals("", mainPasswordRepository.hash)
    }
}
