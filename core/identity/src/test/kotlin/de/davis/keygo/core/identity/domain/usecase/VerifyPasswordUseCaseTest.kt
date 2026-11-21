package de.davis.keygo.core.identity.domain.usecase

import de.davis.keygo.core.identity.FakeAccountRepository
import de.davis.keygo.core.identity.domain.model.Account
import de.davis.keygo.core.identity.domain.model.PasswordWrappedArk
import de.davis.keygo.core.identity.domain.model.VerifyPasswordError
import de.davis.keygo.core.security.FakeSession
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.isSuccess
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VerifyPasswordUseCaseTest {

    private val session = FakeSession()
    private val accountRepository = FakeAccountRepository()

    private val useCase = VerifyPasswordUseCase(
        session = session,
        accountRepository = accountRepository,
    )

    private suspend fun seedAccount(password: String) {
        val created = checkNotNull(session.createAccount(password).getOrNull())
        accountRepository.seed(
            Account(
                id = created.userId,
                displayName = "Test",
                passwordWrappedArk = PasswordWrappedArk(
                    key = created.passwordWrappedArk.ciphertext,
                    keyIV = created.passwordWrappedArk.nonce,
                    salt = created.salt,
                ),
                biometricWrappedArk = null,
            ),
        )
    }

    @Test
    fun `succeeds for the password the account was created with`() = runTest {
        seedAccount("secret")

        assertTrue(useCase("secret").isSuccess())
    }

    @Test
    fun `returns IncorrectPassword for any other password`() = runTest {
        seedAccount("secret")

        val result = useCase("wrong")

        assertTrue(result.isFailure())
        assertEquals(VerifyPasswordError.IncorrectPassword, result.error)
    }

    @Test
    fun `returns IncorrectPassword when the stored ARK is not the one the session holds`() =
        runTest {
            seedAccount("secret")
            session.unlockWithArk(ByteArray(32) { (it + 7).toByte() })

            val result = useCase("secret")

            assertTrue(result.isFailure())
            assertEquals(VerifyPasswordError.IncorrectPassword, result.error)
        }

    @Test
    fun `returns ActiveAccountNotFound when no account is registered`() = runTest {
        val result = useCase("secret")

        assertTrue(result.isFailure())
        assertEquals(VerifyPasswordError.ActiveAccountNotFound, result.error)
    }

    @Test
    fun `returns ActiveAccountNotFound when the session is locked`() = runTest {
        seedAccount("secret")
        session.endSession()

        val result = useCase("secret")

        assertTrue(result.isFailure())
        assertEquals(VerifyPasswordError.ActiveAccountNotFound, result.error)
    }

    @Test
    fun `returns KeyDerivationFailed when derivation fails`() = runTest {
        seedAccount("secret")
        session.failDerivation = true

        val result = useCase("secret")

        assertTrue(result.isFailure())
        assertEquals(VerifyPasswordError.KeyDerivationFailed, result.error)
    }
}
