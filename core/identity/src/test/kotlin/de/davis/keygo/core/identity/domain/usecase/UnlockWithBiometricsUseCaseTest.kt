package de.davis.keygo.core.identity.domain.usecase

import de.davis.keygo.core.biometrics.FakeBiometricCrypto
import de.davis.keygo.core.biometrics.domain.model.BiometricAuthError
import de.davis.keygo.core.biometrics.domain.model.BiometricPolicy
import de.davis.keygo.core.biometrics.domain.model.BiometricString
import de.davis.keygo.core.identity.FakeAccountRepository
import de.davis.keygo.core.identity.domain.mapper.toBiometricWrappedArk
import de.davis.keygo.core.identity.domain.model.Account
import de.davis.keygo.core.identity.domain.model.PasswordWrappedArk
import de.davis.keygo.core.identity.domain.model.UnlockError
import de.davis.keygo.core.security.FakeSession
import de.davis.keygo.core.security.crypto.FakeKeyStoreManager
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.util.assertFailure
import de.davis.keygo.core.util.assertSuccess
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnlockWithBiometricsUseCaseTest {

    private val session = FakeSession()
    private val accountRepository = FakeAccountRepository()
    private val keyStoreManager = FakeKeyStoreManager()
    private val biometricCrypto = FakeBiometricCrypto(keyStoreManager)

    private val ark = ByteArray(32) { (it + 1).toByte() }

    private fun unlockOver(session: FakeSession) = UnlockWithBiometricsUseCase(
        session = session,
        accountRepository = accountRepository,
        biometricCrypto = biometricCrypto,
        disableBiometrics = DisableBiometricsUseCase(accountRepository, keyStoreManager),
    )

    private val unlockWithBiometrics = unlockOver(session)

    private suspend fun seedAccount(biometricWrappedArk: Boolean) {
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
        if (biometricWrappedArk) seedEnrollment(ark)
    }

    private suspend fun seedEnrollment(wrappedKey: ByteArray) {
        val wrapped = biometricCrypto.requestWrap(KeyId.BiometricVaultKek, wrappedKey)
            .assertSuccess()
            .toBiometricWrappedArk()
        accountRepository.seed(accountRepository.getOrNull()!!.copy(biometricWrappedArk = wrapped))
        biometricCrypto.prompts.clear()
    }

    @Test
    fun `returns ActiveAccountNotFound without prompting when no account exists`() = runTest {
        val error = unlockWithBiometrics().assertFailure()

        assertEquals(UnlockError.ActiveAccountNotFound, error)
        assertTrue(biometricCrypto.prompts.isEmpty())
    }

    @Test
    fun `returns WrappedKeyNotFound without prompting when the account is not enrolled`() =
        runTest {
            seedAccount(biometricWrappedArk = false)

            val error = unlockWithBiometrics().assertFailure()

            assertEquals(UnlockError.WrappedKeyNotFound, error)
            assertTrue(biometricCrypto.prompts.isEmpty())
        }

    @Test
    fun `on success starts the session over the recovered ARK`() = runTest {
        seedAccount(biometricWrappedArk = true)

        unlockWithBiometrics().assertSuccess()

        assertTrue(session.isActive.value)
        assertTrue(session.verifyArk(ark).assertSuccess())
    }

    @Test
    fun `unwraps with the biometric key under the policy it was given`() = runTest {
        seedAccount(biometricWrappedArk = true)
        val policy = BiometricPolicy(
            title = BiometricString.Title.UnlockItem("GitHub"),
            negativeButton = BiometricString.NegativeButton.Password,
        )

        unlockWithBiometrics(policy)

        val prompt = biometricCrypto.prompts.single()
        assertEquals(KeyId.BiometricVaultKek, prompt.keyId)
        assertEquals(CryptographicMode.Unwrap, prompt.mode)
        assertEquals(policy, prompt.policy)
    }

    @Test
    fun `returns BiometricFailed with the prompt's error and stays locked`() = runTest {
        seedAccount(biometricWrappedArk = true)
        biometricCrypto.promptFailure = BiometricAuthError.Declined

        val error = unlockWithBiometrics().assertFailure()

        assertEquals(UnlockError.BiometricFailed(BiometricAuthError.Declined), error)
        assertFalse(session.isActive.value)
    }

    @Test
    fun `a retryable biometric failure leaves the stored enrollment in place`() = runTest {
        seedAccount(biometricWrappedArk = true)
        biometricCrypto.promptFailure = BiometricAuthError.CryptoFailed

        val error = unlockWithBiometrics().assertFailure()

        assertEquals(UnlockError.BiometricFailed(BiometricAuthError.CryptoFailed), error)
        assertNotNull(accountRepository.getOrNull()?.biometricWrappedArk)
        assertTrue(KeyId.BiometricVaultKek in keyStoreManager.keys)
    }

    @Test
    fun `an invalidated key drops the stored enrollment and reports it as reset`() = runTest {
        seedAccount(biometricWrappedArk = true)
        keyStoreManager.deleteKey(KeyId.BiometricVaultKek)

        val error = unlockWithBiometrics().assertFailure()

        assertEquals(UnlockError.BiometricEnrollmentReset, error)
        assertNull(accountRepository.getOrNull()?.biometricWrappedArk)
        assertFalse(KeyId.BiometricVaultKek in keyStoreManager.keys)
        assertFalse(session.isActive.value)
    }

    @Test
    fun `a teardown that does not persist is not reported as a reset`() = runTest {
        seedAccount(biometricWrappedArk = true)
        biometricCrypto.promptFailure = BiometricAuthError.KeyInvalidated
        accountRepository.setFails = true

        val error = unlockWithBiometrics().assertFailure()

        assertEquals(UnlockError.BiometricFailed(BiometricAuthError.KeyInvalidated), error)
        assertNotNull(accountRepository.getOrNull()?.biometricWrappedArk)
        assertTrue(KeyId.BiometricVaultKek in keyStoreManager.keys)
    }

    /**
     * Unlocking is the inbound half of the two Keystore doors: the biometric cipher runs JVM-side,
     * so the ARK exists here as a plain array before Rust takes custody of it. [FakeSession] keeps
     * the array it was handed rather than copying, which is what makes the wipe observable.
     *
     * Note this covers only the copy this code owns. `SecretKeySpec.getEncoded` hands back a fresh
     * copy each call, so JCA still holds one that no `fill(0)` here can reach.
     */
    @Test
    fun `wipes the recovered ARK once the session has taken it`() = runTest {
        seedAccount(biometricWrappedArk = true)

        unlockWithBiometrics().assertSuccess()

        assertContentEquals(ByteArray(32), session.handedOver)
    }

    @Test
    fun `wipes the recovered ARK even when the session rejects it`() = runTest {
        val rejecting = FakeSession().apply { failUnlock = true }
        seedAccount(biometricWrappedArk = true)

        val result = unlockOver(rejecting)()

        assertEquals(UnlockError.UnwrappingFailed, result.assertFailure())
        assertContentEquals(ByteArray(32), rejecting.handedOver)
    }

    @Test
    fun `returns UnwrappingFailed and stays locked when the recovered key is not an ARK`() =
        runTest {
            seedAccount(biometricWrappedArk = false)
            seedEnrollment(ByteArray(16) { 1 })

            val error = unlockWithBiometrics().assertFailure()

            assertEquals(UnlockError.UnwrappingFailed, error)
            assertFalse(session.isActive.value)
        }
}
