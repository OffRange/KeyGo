package de.davis.keygo.feature.credentials.presentation.create.activity

import de.davis.keygo.core.identity.FakeAccountRepository
import de.davis.keygo.core.identity.domain.model.Account
import de.davis.keygo.core.identity.domain.model.BiometricWrappedArk
import de.davis.keygo.core.identity.domain.model.PasswordWrappedArk
import de.davis.keygo.core.identity.domain.model.UnlockError
import de.davis.keygo.core.item.FakeLoginRepository
import de.davis.keygo.core.item.FakePasskeyRepository
import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.security.crypto.FakeBiometricAvailabilityRepository
import de.davis.keygo.core.security.crypto.FakeCryptographicScopeProvider
import de.davis.keygo.core.security.domain.model.BiometricAuthError
import de.davis.keygo.feature.credentials.presentation.auth.SessionAuthState
import de.davis.keygo.rust.FakePasskeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class CreatePasskeyViewModelTest {

    private val passkeyRepository = FakePasskeyRepository()
    private val loginRepository = FakeLoginRepository()
    private val vaultRepository = FakeVaultRepository()
    private val cryptographicScopeProvider = FakeCryptographicScopeProvider()
    private val passkeyManager = FakePasskeyManager()
    private val accountRepository = FakeAccountRepository()
    private val biometricAvailabilityRepository = FakeBiometricAvailabilityRepository()

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun account(withBiometric: Boolean) = Account(
        id = UUID.randomUUID(),
        displayName = "Test",
        passwordWrappedArk = PasswordWrappedArk(
            key = byteArrayOf(1),
            keyIV = byteArrayOf(2),
            salt = byteArrayOf(3),
        ),
        biometricWrappedArk = if (withBiometric)
            BiometricWrappedArk(key = byteArrayOf(4), keyIV = byteArrayOf(5))
        else null,
    )

    private fun newViewModel() = CreatePasskeyViewModel(
        passkeyRepository = passkeyRepository,
        loginRepository = loginRepository,
        vaultRepository = vaultRepository,
        cryptographicScopeProvider = cryptographicScopeProvider,
        passkeyManager = passkeyManager,
        accountRepository = accountRepository,
        biometricAvailabilityRepository = biometricAvailabilityRepository,
    )

    @Test
    fun `init sets TryBiometric and emits biometricFlow when biometric usable`() = runTest {
        accountRepository.seed(account(withBiometric = true))
        biometricAvailabilityRepository.isAvailable = true

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(SessionAuthState.TryBiometric, viewModel.authState.value)
        assertNotNull(viewModel.biometricFlow.first())
    }

    @Test
    fun `init sets NeedsPassword when account has no biometricWrappedArk`() = runTest {
        accountRepository.seed(account(withBiometric = false))
        biometricAvailabilityRepository.isAvailable = true

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(SessionAuthState.NeedsPassword, viewModel.authState.value)
    }

    @Test
    fun `init sets NeedsPassword when biometric hardware unavailable`() = runTest {
        accountRepository.seed(account(withBiometric = true))
        biometricAvailabilityRepository.isAvailable = false

        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(SessionAuthState.NeedsPassword, viewModel.authState.value)
    }

    @Test
    fun `onUnlockFailed with CanNotAuthenticate transitions to NeedsPassword`() = runTest {
        accountRepository.seed(account(withBiometric = true))
        biometricAvailabilityRepository.isAvailable = true

        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.onUnlockFailed(
            UnlockError.BiometricFailed(BiometricAuthError.CanNotAuthenticate(code = 12))
        )
        advanceUntilIdle()

        assertEquals(SessionAuthState.NeedsPassword, viewModel.authState.value)
    }

    @Test
    fun `onUnlockFailed with NoCipher emits Abort event`() = runTest {
        accountRepository.seed(account(withBiometric = true))
        biometricAvailabilityRepository.isAvailable = true

        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.onUnlockFailed(UnlockError.BiometricFailed(BiometricAuthError.NoCipher))
        advanceUntilIdle()

        val event = viewModel.event.first()
        assertEquals(CreatePasskeyEvent.Abort, event)
    }

    @Test
    fun `onUnlocked transitions to Authenticated`() = runTest {
        accountRepository.seed(account(withBiometric = true))
        biometricAvailabilityRepository.isAvailable = true

        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.onUnlocked()
        advanceUntilIdle()

        assertEquals(SessionAuthState.Authenticated, viewModel.authState.value)
    }
}
