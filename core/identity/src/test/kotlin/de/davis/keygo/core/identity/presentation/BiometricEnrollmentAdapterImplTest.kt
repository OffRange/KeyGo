package de.davis.keygo.core.identity.presentation

import de.davis.keygo.core.identity.FakeAccountRepository
import de.davis.keygo.core.identity.domain.model.Account
import de.davis.keygo.core.identity.domain.model.BiometricEnrollmentError
import de.davis.keygo.core.identity.domain.model.PasswordWrappedArk
import de.davis.keygo.core.security.crypto.FakeBiometricCryptoController
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.model.BiometricAuthError
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.isSuccess
import de.davis.keygo.rust.RecordingArkSession
import kotlinx.coroutines.test.runTest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Enrolment is one of only three places the ARK crosses into the JVM, because the Keystore cipher
 * that seals the biometric copy only runs on this side of the FFI. The `finally` that zeroes the
 * exported array is the sole thing keeping that copy from staying resident, so it is asserted
 * directly here through [RecordingArkSession], which hands out its array rather than a copy.
 */
class BiometricEnrollmentAdapterImplTest {

    private val arkSession = RecordingArkSession(startUnlocked = true)
    private val session = Session(arkSession)
    private val accountRepository = FakeAccountRepository()
    private val controller = FakeBiometricCryptoController()

    private val adapter = BiometricEnrollmentAdapterImpl(
        accountRepository = accountRepository,
        session = session,
    )

    private fun seedAccount() = accountRepository.seed(
        Account(
            id = UUID.randomUUID(),
            displayName = "Test",
            passwordWrappedArk = PasswordWrappedArk(
                key = ByteArray(48) { 1 },
                keyIV = ByteArray(12) { 2 },
                salt = ByteArray(16) { 3 },
            ),
            biometricWrappedArk = null,
        ),
    )

    private fun wrappingCipher() = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(Cipher.WRAP_MODE, KeyGenerator.getInstance("AES").apply { init(256) }.generateKey())
    }

    private suspend fun enroll() = with(adapter) { controller.requestEnableBiometric() }

    @Test
    fun `enrolling persists a biometric-wrapped ARK`() = runTest {
        seedAccount()
        controller.cipherResult = Result.Success(wrappingCipher())

        val result = enroll()

        assertTrue(result.isSuccess())
        val wrapped = assertNotNull(accountRepository.getOrNull()?.biometricWrappedArk)
        assertTrue(wrapped.key.isNotEmpty())
        assertTrue(wrapped.keyIV.isNotEmpty())
    }

    @Test
    fun `wipes the exported ARK once it has been wrapped`() = runTest {
        seedAccount()
        controller.cipherResult = Result.Success(wrappingCipher())

        enroll()

        assertContentEquals(ByteArray(32), arkSession.onlyExported())
    }

    @Test
    fun `wipes the exported ARK even when wrapping fails`() = runTest {
        seedAccount()
        // A cipher in the wrong mode makes Cipher.wrap throw, after the ARK has been exported.
        val wrongMode = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, KeyGenerator.getInstance("AES").apply { init(256) }.generateKey())
        }
        controller.cipherResult = Result.Success(wrongMode)

        val result = enroll()

        assertTrue(result.isFailure())
        assertEquals(BiometricEnrollmentError.WrappingFailed, result.error)
        assertContentEquals(ByteArray(32), arkSession.onlyExported())
    }

    @Test
    fun `a locked session reports NoActiveSession and never persists`() = runTest {
        seedAccount()
        controller.cipherResult = Result.Success(wrappingCipher())
        session.endSession()

        val result = enroll()

        assertTrue(result.isFailure())
        assertEquals(BiometricEnrollmentError.NoActiveSession, result.error)
        assertNull(accountRepository.getOrNull()?.biometricWrappedArk)
    }

    @Test
    fun `no account reports NoActiveAccount without touching the session`() = runTest {
        controller.cipherResult = Result.Success(wrappingCipher())

        val result = enroll()

        assertTrue(result.isFailure())
        assertEquals(BiometricEnrollmentError.NoActiveAccount, result.error)
        assertTrue(arkSession.exported.isEmpty())
    }

    @Test
    fun `a biometric failure is reported without exporting the ARK`() = runTest {
        seedAccount()
        controller.cipherResult = Result.Failure(BiometricAuthError.NoCipher)

        val result = enroll()

        assertTrue(result.isFailure())
        assertEquals(
            BiometricEnrollmentError.BiometricFailed(BiometricAuthError.NoCipher),
            result.error,
        )
        assertTrue(arkSession.exported.isEmpty())
    }

    @Test
    fun `disabling biometrics clears the stored wrapped ARK`() = runTest {
        seedAccount()
        controller.cipherResult = Result.Success(wrappingCipher())
        enroll()

        val result = adapter.disableBiometric()

        assertTrue(result.isSuccess())
        assertNull(accountRepository.getOrNull()?.biometricWrappedArk)
    }
}
