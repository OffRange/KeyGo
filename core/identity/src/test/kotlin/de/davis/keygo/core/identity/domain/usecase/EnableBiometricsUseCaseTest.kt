@file:OptIn(ExportArk::class)

package de.davis.keygo.core.identity.domain.usecase

import de.davis.keygo.core.biometrics.FakeBiometricCrypto
import de.davis.keygo.core.biometrics.domain.model.BiometricAuthError
import de.davis.keygo.core.biometrics.domain.model.BiometricEnrollmentError
import de.davis.keygo.core.biometrics.domain.model.BiometricPolicy
import de.davis.keygo.core.biometrics.domain.model.BiometricString
import de.davis.keygo.core.identity.FakeAccountRepository
import de.davis.keygo.core.identity.domain.mapper.toBiometricWrappedArk
import de.davis.keygo.core.identity.domain.model.Account
import de.davis.keygo.core.identity.domain.model.BiometricWrappedArk
import de.davis.keygo.core.identity.domain.model.PasswordWrappedArk
import de.davis.keygo.core.security.FakeSession
import de.davis.keygo.core.security.crypto.FakeKeyStoreManager
import de.davis.keygo.core.security.domain.ExportArk
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.util.assertFailure
import de.davis.keygo.core.util.assertSuccess
import de.davis.keygo.core.util.getOrNull
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Enrolment is one of only three places the ARK crosses into the JVM, because the Keystore cipher
 * that seals the biometric copy only runs on this side of the FFI. The `finally` that zeroes the
 * exported array is the sole thing keeping that copy from staying resident, so it is asserted
 * directly here through [FakeSession], which hands out its array rather than a copy.
 */
class EnableBiometricsUseCaseTest {

    private val session = FakeSession(startUnlocked = true)
    private val accountRepository = FakeAccountRepository()
    private val keyStoreManager = FakeKeyStoreManager()
    private val biometricCrypto = FakeBiometricCrypto(keyStoreManager)

    private val enableBiometrics = EnableBiometricsUseCase(
        accountRepository = accountRepository,
        session = session,
        keyStoreManager = keyStoreManager,
        biometricCrypto = biometricCrypto,
    )

    private fun seedAccount(biometricWrappedArk: BiometricWrappedArk? = null) =
        accountRepository.seed(
            Account(
                id = UUID.randomUUID(),
                displayName = "Test",
                passwordWrappedArk = PasswordWrappedArk(
                    key = ByteArray(48) { 1 },
                    keyIV = ByteArray(12) { 2 },
                    salt = ByteArray(16) { 3 },
                ),
                biometricWrappedArk = biometricWrappedArk,
            ),
        )

    private suspend fun seedEnrolledAccount() {
        val ark = checkNotNull(session.exportArk().getOrNull())
        seedAccount(
            biometricWrappedArk = biometricCrypto.requestWrap(KeyId.BiometricVaultKek, ark)
                .assertSuccess()
                .toBiometricWrappedArk(),
        )
        session.exported.clear()
        biometricCrypto.prompts.clear()
    }

    private suspend fun opensToLiveArk(wrapped: BiometricWrappedArk): Boolean {
        val recovered = biometricCrypto.requestUnwrap(
            keyId = KeyId.BiometricVaultKek,
            cryptographicData = CryptographicData(data = wrapped.key, iv = wrapped.keyIV),
        ).getOrNull() ?: return false
        return session.verifyArk(recovered.encoded).assertSuccess()
    }

    @Test
    fun `enrolling persists a biometric-wrapped ARK that opens back to the live ARK`() = runTest {
        seedAccount()

        enableBiometrics().assertSuccess()

        val wrapped = assertNotNull(accountRepository.getOrNull()?.biometricWrappedArk)
        assertTrue(opensToLiveArk(wrapped))
    }

    @Test
    fun `enrolling wraps under the biometric key with the policy it was given`() = runTest {
        seedAccount()
        val policy = BiometricPolicy(title = BiometricString.Title.Authenticate)

        enableBiometrics(policy)

        val prompt = biometricCrypto.prompts.single()
        assertEquals(KeyId.BiometricVaultKek, prompt.keyId)
        assertEquals(CryptographicMode.Wrap, prompt.mode)
        assertEquals(policy, prompt.policy)
    }

    @Test
    fun `wipes the exported ARK once it has been wrapped`() = runTest {
        seedAccount()

        enableBiometrics()

        assertContentEquals(ByteArray(32), session.onlyExported())
    }

    @Test
    fun `wipes the exported ARK even when the prompt fails`() = runTest {
        seedAccount()
        biometricCrypto.promptFailure = BiometricAuthError.LockedOut

        enableBiometrics().assertFailure()

        assertContentEquals(ByteArray(32), session.onlyExported())
    }

    @Test
    fun `a declined prompt is reported as a dismissal and persists nothing`() = runTest {
        seedAccount()
        biometricCrypto.promptFailure = BiometricAuthError.Declined

        val error = enableBiometrics().assertFailure()

        assertEquals(BiometricEnrollmentError.BiometricFailed(BiometricAuthError.Declined), error)
        assertNull(accountRepository.getOrNull()?.biometricWrappedArk)
    }

    @Test
    fun `a locked session reports NoActiveSession without prompting and never persists`() =
        runTest {
            seedAccount()
            session.endSession()

            val error = enableBiometrics().assertFailure()

            assertEquals(BiometricEnrollmentError.NoActiveSession, error)
            assertTrue(biometricCrypto.prompts.isEmpty())
            assertNull(accountRepository.getOrNull()?.biometricWrappedArk)
        }

    @Test
    fun `no account reports NoActiveAccount without touching the session or the prompt`() =
        runTest {
            val error = enableBiometrics().assertFailure()

            assertEquals(BiometricEnrollmentError.NoActiveAccount, error)
            assertTrue(session.exported.isEmpty())
            assertTrue(biometricCrypto.prompts.isEmpty())
        }

    @Test
    fun `enrolling without an account leaves the keystore alone`() = runTest {
        keyStoreManager.getOrCreateCipherFor(KeyId.BiometricVaultKek, CryptographicMode.Wrap)

        enableBiometrics().assertFailure()

        assertTrue(KeyId.BiometricVaultKek in keyStoreManager.keys)
    }

    @Test
    fun `returns PersistenceFailed when the account cannot be saved`() = runTest {
        seedAccount()
        accountRepository.setFails = true

        val error = enableBiometrics().assertFailure()

        assertEquals(BiometricEnrollmentError.PersistenceFailed, error)
        assertNull(accountRepository.getOrNull()?.biometricWrappedArk)
    }

    @Test
    fun `a failed enrollment leaves the stored enrollment intact`() = runTest {
        seedEnrolledAccount()
        val inUse = keyStoreManager.keys.getValue(KeyId.BiometricVaultKek)
        val before = accountRepository.getOrNull()!!.biometricWrappedArk!!
        // The prompt fails, as a user declining it would. The stored ARK is still wrapped under
        // this key, so taking it down here would strand an enrollment that works.
        biometricCrypto.promptFailure = BiometricAuthError.Declined

        enableBiometrics().assertFailure()

        assertSame(inUse, keyStoreManager.keys[KeyId.BiometricVaultKek])
        val after = assertNotNull(accountRepository.getOrNull()?.biometricWrappedArk)
        assertContentEquals(before.key, after.key)
        biometricCrypto.promptFailure = null
        assertTrue(opensToLiveArk(after))
    }

    @Test
    fun `enrolling again while enrolled rewraps under the key already in use`() = runTest {
        seedEnrolledAccount()
        val inUse = keyStoreManager.keys.getValue(KeyId.BiometricVaultKek)

        enableBiometrics().assertSuccess()

        assertSame(inUse, keyStoreManager.keys[KeyId.BiometricVaultKek])
        assertTrue(opensToLiveArk(accountRepository.getOrNull()!!.biometricWrappedArk!!))
    }

    /**
     * The interrupted-disable case: the wrapped ARK is gone but its key survived. Nothing can open
     * that key any more, so an enrollment starting here must not adopt it - on the devices this
     * exists for, adopting it is how the unusable key comes back.
     */
    @Test
    fun `enrolling from an unenrolled account drops the key left behind`() = runTest {
        seedAccount()
        keyStoreManager.getOrCreateCipherFor(KeyId.BiometricVaultKek, CryptographicMode.Wrap)
        // The prompt fails afterwards, so what is left on the keystore is what enrollment decided
        // to start from: nothing.
        biometricCrypto.promptFailure = BiometricAuthError.Declined

        enableBiometrics()

        assertFalse(KeyId.BiometricVaultKek in keyStoreManager.keys)
    }

    @Test
    fun `enrolling from an unenrolled account wraps under a fresh key`() = runTest {
        seedAccount()
        keyStoreManager.getOrCreateCipherFor(KeyId.BiometricVaultKek, CryptographicMode.Wrap)
        val leftBehind = keyStoreManager.keys.getValue(KeyId.BiometricVaultKek)

        enableBiometrics().assertSuccess()

        assertNotSame(leftBehind, keyStoreManager.keys[KeyId.BiometricVaultKek])
        assertTrue(opensToLiveArk(accountRepository.getOrNull()!!.biometricWrappedArk!!))
    }
}
