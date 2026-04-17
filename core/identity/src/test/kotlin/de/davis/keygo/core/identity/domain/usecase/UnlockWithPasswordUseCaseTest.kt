package de.davis.keygo.core.identity.domain.usecase

import de.davis.keygo.core.identity.FakeAccountRepository
import de.davis.keygo.core.identity.domain.model.Account
import de.davis.keygo.core.identity.domain.model.PasswordWrappedArk
import de.davis.keygo.core.identity.domain.model.UnlockError
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.crypto.model.AesKey
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.isSuccess
import de.davis.keygo.rust.FakeKeyDeriver
import de.davis.keygo.rust.FakeKeyWrapper
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnlockWithPasswordUseCaseTest {

    private val session = mockk<Session>(relaxed = true)
    private val accountRepository = FakeAccountRepository()
    private val keyDeriver = FakeKeyDeriver()
    private val keyWrapper = FakeKeyWrapper()

    private val useCase = UnlockWithPasswordUseCase(
        session = session,
        accountRepository = accountRepository,
        keyDeriver = keyDeriver,
        keyWrapper = keyWrapper,
    )

    private fun seedAccount(
        password: String,
        accountId: UUID = UUID.randomUUID(),
        ark: ByteArray = ByteArray(32) { it.toByte() },
    ): Account {
        val salt = keyDeriver.generateSalt()
        val kek = keyDeriver.deriveRootKekFromPassword(password, salt)
        val wrapped = keyWrapper.wrapAccountRootKey(kek, ark, accountId)

        val account = Account(
            id = accountId,
            displayName = "Test",
            passwordWrappedArk = PasswordWrappedArk(
                key = wrapped.ciphertext,
                keyIV = wrapped.nonce,
                salt = salt,
            ),
            biometricWrappedArk = null,
        )
        accountRepository.seed(account)
        return account
    }

    @Test
    fun `returns ActiveAccountNotFound when no account is registered`() = runTest {
        val result = useCase("password")

        assertTrue(result.isFailure())
        assertEquals(UnlockError.ActiveAccountNotFound, result.error)
    }

    @Test
    fun `returns DerivationFailed when key derivation fails`() = runTest {
        seedAccount("password")
        keyDeriver.failDerivation = true

        val result = useCase("password")

        assertTrue(result.isFailure())
        assertEquals(UnlockError.DerivationFailed, result.error)
    }

    @Test
    fun `returns UnwrappingFailed when wrong password is used`() = runTest {
        seedAccount("password")

        val result = useCase("wrong-password")

        assertTrue(result.isFailure())
        assertEquals(UnlockError.UnwrappingFailed, result.error)
    }

    @Test
    fun `returns Success and starts session with correct password`() = runTest {
        val ark = ByteArray(32) { (it + 1).toByte() }
        seedAccount("password", ark = ark)

        val result = useCase("password")

        assertTrue(result.isSuccess())
        val dekSlot = slot<AesKey>()
        verify { session.startSession(capture(dekSlot)) }
        assertTrue(dekSlot.captured.key.encoded.contentEquals(ark))
    }
}
