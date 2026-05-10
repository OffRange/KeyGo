package de.davis.keygo.core.identity.presentation

import de.davis.keygo.core.identity.FakeAccountRepository
import de.davis.keygo.core.identity.domain.model.Account
import de.davis.keygo.core.identity.domain.model.BiometricWrappedArk
import de.davis.keygo.core.identity.domain.model.PasswordWrappedArk
import de.davis.keygo.core.identity.domain.model.UnlockError
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.model.BiometricAuthError
import de.davis.keygo.core.security.domain.model.BiometricPolicy
import de.davis.keygo.core.security.domain.model.CiphertextData
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.security.presentation.BiometricCryptoController
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.isSuccess
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import java.util.UUID
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BiometricUnlockAdapterImplTest {

    private val session = mockk<Session>(relaxed = true)
    private val accountRepository = FakeAccountRepository()
    private val controller = mockk<BiometricCryptoController>()

    private val adapter = BiometricUnlockAdapterImpl(
        session = session,
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
            coEvery {
                controller.requestUnwrap(any(), any(), any())
            } returns Result.Failure(biometricError)

            val result = with(adapter) { controller.requestUnlockVault(BiometricPolicy.Default) }

            assertTrue(result.isFailure())
            assertEquals(UnlockError.BiometricFailed(biometricError), result.error)
        }

    @Test
    fun `returns BiometricFailed(NoCipher) when manager refuses with NoCipher`() = runTest {
        seedAccountWithBiometric()
        coEvery {
            controller.requestUnwrap(any(), any(), any())
        } returns Result.Failure(BiometricAuthError.NoCipher)

        val result = with(adapter) { controller.requestUnlockVault(BiometricPolicy.Default) }

        assertTrue(result.isFailure())
        assertEquals(UnlockError.BiometricFailed(BiometricAuthError.NoCipher), result.error)
    }

    @Test
    fun `on success starts session and returns Success`() = runTest {
        seedAccountWithBiometric()
        val key = SecretKeySpec(ByteArray(32) { 1 }, "AES")
        coEvery {
            controller.requestUnwrap(
                keyId = KeyId.BiometricVaultKek,
                ciphertextData = any(),
                policy = any(),
            )
        } returns Result.Success(key)

        val result = with(adapter) { controller.requestUnlockVault(BiometricPolicy.Default) }

        assertTrue(result.isSuccess())
        verify { session.startSession(any()) }
    }
}
