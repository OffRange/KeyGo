package de.davis.keygo.core.identity.domain.usecase

import de.davis.keygo.core.identity.FakeAccountRepository
import de.davis.keygo.core.identity.domain.model.Account
import de.davis.keygo.core.identity.domain.model.PasswordWrappedArk
import de.davis.keygo.core.identity.domain.model.UnlockError
import de.davis.keygo.core.security.FakeSession
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.isSuccess
import de.davisalessandro.keygo.rust.NewAccount
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnlockWithPasswordUseCaseTest {

    private val session = FakeSession()
    private val accountRepository = FakeAccountRepository()

    private val useCase = UnlockWithPasswordUseCase(
        session = session,
        accountRepository = accountRepository,
    )

    /**
     * Mints an account through the session, persists what the app would persist, then locks the
     * session again so the use case has something to unlock.
     */
    private suspend fun seedAccount(password: String): NewAccount {
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

        session.endSession()
        return created
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
        session.failDerivation = true

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
        assertFalse(session.isActive.value)
    }

    @Test
    fun `returns Success and starts session with correct password`() = runTest {
        val created = seedAccount("password")

        val result = useCase("password")

        assertTrue(result.isSuccess())
        assertTrue(session.isActive.value)
        // The recovered ARK is the one the account was created under: it still unwraps the
        // default vault's key.
        assertTrue(session.unwrapVaultKey(created.wrappedVaultKey, created.vaultId).isSuccess())
    }
}
