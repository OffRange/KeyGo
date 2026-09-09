package de.davis.keygo.core.identity.presentation

import de.davis.keygo.core.identity.FakeAccountRepository
import de.davis.keygo.core.identity.domain.model.Account
import de.davis.keygo.core.identity.domain.model.BiometricWrappedArk
import de.davis.keygo.core.identity.domain.model.PasswordWrappedArk
import de.davis.keygo.core.identity.domain.model.UnlockError
import de.davis.keygo.core.security.crypto.FakeBiometricCryptoController
import de.davis.keygo.core.security.crypto.FakeKeyStoreManager
import de.davis.keygo.core.security.crypto.FakeSession
import de.davis.keygo.core.security.domain.model.BiometricAuthError
import de.davis.keygo.core.security.domain.model.BiometricPolicy
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.isSuccess
import kotlinx.coroutines.test.runTest
import java.util.UUID
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BiometricUnlockAdapterImplTest {

    private val session = FakeSession()
    private val accountRepository = FakeAccountRepository()
    private val controller = FakeBiometricCryptoController()
    private val keyStoreManager = FakeKeyStoreManager()

    private val enrollmentAdapter = BiometricEnrollmentAdapterImpl(
        accountRepository = accountRepository,
        session = session,
        keyStoreManager = keyStoreManager,
    )

    private val adapter = BiometricUnlockAdapterImpl(
        session = session,
        accountRepository = accountRepository,
        biometricEnrollmentAdapter = enrollmentAdapter,
    )

    private fun seedAccountWithBiometric() {
        keyStoreManager.getOrCreateCipherFor(KeyId.BiometricVaultKek, CryptographicMode.Wrap)
        accountRepository.seed(
            Account(
                id = UUID.randomUUID(),
                displayName = "Test",
                passwordWrappedArk = PasswordWrappedArk(
                    key = byteArrayOf(1),
                    keyIV = byteArrayOf(2),
                    salt = byteArrayOf(3),
                ),
                biometricWrappedArk = BiometricWrappedArk(
                    key = byteArrayOf(4),
                    keyIV = byteArrayOf(5),
                ),
            )
        )
    }

    @Test
    fun `returns WrappedKeyNotFound when account has no biometricWrappedArk`() = runTest {
        accountRepository.seed(
            Account(
                id = UUID.randomUUID(),
                displayName = "Test",
                passwordWrappedArk = PasswordWrappedArk(
                    key = byteArrayOf(1),
                    keyIV = byteArrayOf(2),
                    salt = byteArrayOf(3),
                ),
                biometricWrappedArk = null,
            )
        )

        val result = with(adapter) { controller.requestUnlockVault(BiometricPolicy.Default) }

        assertTrue(result.isFailure())
        assertEquals(UnlockError.WrappedKeyNotFound, result.error)
    }

    @Test
    fun `returns BiometricFailed with the underlying BiometricError code on unwrap failure`() =
        runTest {
            seedAccountWithBiometric()
            val biometricError = BiometricAuthError.CanNotAuthenticate(code = 12)
            controller.unwrapResult = Result.Failure(biometricError)

            val result = with(adapter) { controller.requestUnlockVault(BiometricPolicy.Default) }

            assertTrue(result.isFailure())
            assertEquals(UnlockError.BiometricFailed(biometricError), result.error)
        }

    @Test
    fun `returns BiometricFailed(NoCipher) when manager refuses with NoCipher`() = runTest {
        seedAccountWithBiometric()
        controller.unwrapResult = Result.Failure(BiometricAuthError.NoCipher)

        val result = with(adapter) { controller.requestUnlockVault(BiometricPolicy.Default) }

        assertTrue(result.isFailure())
        assertEquals(UnlockError.BiometricFailed(BiometricAuthError.NoCipher), result.error)
    }

    @Test
    fun `KeyInvalidated drops the stored enrollment and reports it as reset`() = runTest {
        seedAccountWithBiometric()
        controller.unwrapResult = Result.Failure(BiometricAuthError.KeyInvalidated)

        val result = with(adapter) { controller.requestUnlockVault(BiometricPolicy.Default) }

        assertTrue(result.isFailure())
        assertEquals(UnlockError.BiometricEnrollmentReset, result.error)
        assertNull(accountRepository.getOrNull()?.biometricWrappedArk)
        assertFalse(KeyId.BiometricVaultKek in keyStoreManager.keys)
    }

    @Test
    fun `a teardown that does not persist is not reported as a reset`() = runTest {
        seedAccountWithBiometric()
        controller.unwrapResult = Result.Failure(BiometricAuthError.KeyInvalidated)
        accountRepository.setFails = true

        val result = with(adapter) { controller.requestUnlockVault(BiometricPolicy.Default) }

        assertTrue(result.isFailure())
        assertEquals(UnlockError.BiometricFailed(BiometricAuthError.KeyInvalidated), result.error)
        assertNotNull(accountRepository.getOrNull()?.biometricWrappedArk)
        assertTrue(KeyId.BiometricVaultKek in keyStoreManager.keys)
    }

    @Test
    fun `a retryable biometric failure leaves the stored enrollment in place`() = runTest {
        seedAccountWithBiometric()
        controller.unwrapResult = Result.Failure(BiometricAuthError.CryptoFailed)

        val result = with(adapter) { controller.requestUnlockVault(BiometricPolicy.Default) }

        assertTrue(result.isFailure())
        assertEquals(UnlockError.BiometricFailed(BiometricAuthError.CryptoFailed), result.error)
        assertNotNull(accountRepository.getOrNull()?.biometricWrappedArk)
        assertTrue(KeyId.BiometricVaultKek in keyStoreManager.keys)
    }

    @Test
    fun `on success starts session and returns Success`() = runTest {
        seedAccountWithBiometric()
        val key = SecretKeySpec(ByteArray(32) { 1 }, "AES")
        controller.unwrapResult = Result.Success(key)

        val result = with(adapter) { controller.requestUnlockVault(BiometricPolicy.Default) }

        assertTrue(result.isSuccess())
        assertTrue(session.startSessionCalled)
    }
}
