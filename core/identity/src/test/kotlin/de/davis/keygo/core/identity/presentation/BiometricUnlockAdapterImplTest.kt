package de.davis.keygo.core.identity.presentation

import de.davis.keygo.core.identity.FakeAccountRepository
import de.davis.keygo.core.identity.domain.model.Account
import de.davis.keygo.core.identity.domain.model.BiometricWrappedArk
import de.davis.keygo.core.identity.domain.model.PasswordWrappedArk
import de.davis.keygo.core.identity.domain.model.UnlockError
import de.davis.keygo.core.security.crypto.FakeBiometricCryptoController
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.model.BiometricAuthError
import de.davis.keygo.core.security.domain.model.BiometricPolicy
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.isSuccess
import de.davis.keygo.rust.FakeArkSession
import de.davis.keygo.rust.RecordingArkSession
import kotlinx.coroutines.test.runTest
import java.util.UUID
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BiometricUnlockAdapterImplTest {

    private val session = Session(FakeArkSession())
    private val accountRepository = FakeAccountRepository()
    private val controller = FakeBiometricCryptoController()

    private val adapter = BiometricUnlockAdapterImpl(
        session = session,
        accountRepository = accountRepository,
    )

    private fun adapterOver(arkSession: RecordingArkSession) = BiometricUnlockAdapterImpl(
        session = Session(arkSession),
        accountRepository = accountRepository,
    )

    private fun seedAccountWithBiometric() {
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
    fun `on success starts session and returns Success`() = runTest {
        seedAccountWithBiometric()
        val key = SecretKeySpec(ByteArray(32) { 1 }, "AES")
        controller.unwrapResult = Result.Success(key)

        val result = with(adapter) { controller.requestUnlockVault(BiometricPolicy.Default) }

        assertTrue(result.isSuccess())
        assertTrue(session.isActive.value)
    }

    /**
     * Unlocking is the inbound half of the two Keystore doors: the biometric cipher runs JVM-side,
     * so the ARK exists here as a plain array before Rust takes custody of it. [RecordingArkSession]
     * keeps the array it was handed rather than copying, which is what makes the wipe observable.
     *
     * Note this covers only the copy this code owns. `SecretKeySpec.getEncoded` hands back a fresh
     * copy each call, so JCA still holds one that no `fill(0)` here can reach.
     */
    @Test
    fun `wipes the recovered ARK once the session has taken it`() = runTest {
        val recording = RecordingArkSession()
        seedAccountWithBiometric()
        controller.unwrapResult = Result.Success(SecretKeySpec(ByteArray(32) { 1 }, "AES"))

        val result = with(adapterOver(recording)) {
            controller.requestUnlockVault(BiometricPolicy.Default)
        }

        assertTrue(result.isSuccess())
        assertContentEquals(ByteArray(32), recording.handedOver)
    }

    @Test
    fun `wipes the recovered ARK even when the session rejects it`() = runTest {
        val recording = RecordingArkSession().apply { failUnlock = true }
        seedAccountWithBiometric()
        controller.unwrapResult = Result.Success(SecretKeySpec(ByteArray(32) { 1 }, "AES"))

        val result = with(adapterOver(recording)) {
            controller.requestUnlockVault(BiometricPolicy.Default)
        }

        assertTrue(result.isFailure())
        assertContentEquals(ByteArray(32), recording.handedOver)
    }

    @Test
    fun `returns UnwrappingFailed and stays locked when the recovered key is not an ARK`() =
        runTest {
            seedAccountWithBiometric()
            controller.unwrapResult = Result.Success(SecretKeySpec(ByteArray(16) { 1 }, "AES"))

            val result = with(adapter) { controller.requestUnlockVault(BiometricPolicy.Default) }

            assertTrue(result.isFailure())
            assertEquals(UnlockError.UnwrappingFailed, result.error)
            assertFalse(session.isActive.value)
        }
}
