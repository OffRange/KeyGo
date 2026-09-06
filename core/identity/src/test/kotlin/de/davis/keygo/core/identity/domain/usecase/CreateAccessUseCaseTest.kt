package de.davis.keygo.core.identity.domain.usecase

import de.davis.keygo.core.identity.FakeAccountRepository
import de.davis.keygo.core.identity.domain.model.CreateAccessError
import de.davis.keygo.core.item.FakeVaultContextRepository
import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.security.crypto.FakeSession
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.isSuccess
import de.davis.keygo.rust.FakeAccountManager
import de.davis.keygo.rust.FakeKeyDeriver
import de.davis.keygo.rust.FakeKeyWrapper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CreateAccessUseCaseTest {

    private val session = FakeSession()
    private val accountRepository = FakeAccountRepository()
    private val vaultRepository = FakeVaultRepository()
    private val vaultContextRepository = FakeVaultContextRepository()
    private val keyDeriver = FakeKeyDeriver()
    private val keyWrapper = FakeKeyWrapper()
    private val accountManager = FakeAccountManager()

    private val useCase = CreateAccessUseCase(
        keyDeriver = keyDeriver,
        keyWrapper = keyWrapper,
        accountManager = accountManager,
        accountRepository = accountRepository,
        vaultRepository = vaultRepository,
        vaultContextRepository = vaultContextRepository,
        session = session,
    )

    @Test
    fun `returns KeyDerivationFailed when derivation fails`() = runTest {
        keyDeriver.failDerivation = true

        val result = useCase("password")

        assertTrue(result.isFailure())
        assertEquals(CreateAccessError.KeyDerivationFailed, result.error)
    }

    @Test
    fun `returns AccountPersistenceFailed when account repository rejects set`() = runTest {
        accountRepository.setFails = true

        val result = useCase("password")

        assertTrue(result.isFailure())
        assertEquals(CreateAccessError.AccountPersistenceFailed, result.error)
    }

    @Test
    fun `does not persist a vault when account persistence fails`() = runTest {
        accountRepository.setFails = true

        useCase("password")

        assertTrue(vaultRepository.observeVaults().first().isEmpty())
    }

    @Test
    fun `reports VaultPersistenceFailed and leaves account persisted when vault create throws`() =
        runTest {
            val cause = RuntimeException("disk full")
            vaultRepository.createError = cause

            val result = useCase("password")

            assertTrue(result.isFailure())
            val error = result.error
            assertTrue(error is CreateAccessError.VaultPersistenceFailed)
            assertEquals(cause, error.cause)
            // Account is durable so a retry can proceed without orphaning a half-account.
            assertNotNull(accountRepository.getOrNull())
        }

    @Test
    fun `returns Success and starts session without biometric cipher`() = runTest {
        val result = useCase("password", biometricCipher = null)

        assertTrue(result.isSuccess())
        assertTrue(session.startSessionCalled)
        assertContentEquals(accountManager.createAccount.account.ark, session.currentArk)
    }

    @Test
    fun `persists account with password-wrapped ARK on success`() = runTest {
        useCase("password")

        val stored = assertNotNull(accountRepository.getOrNull())
        assertTrue(stored.passwordWrappedArk.key.isNotEmpty())
        assertTrue(stored.passwordWrappedArk.keyIV.isNotEmpty())
        assertEquals(16, stored.passwordWrappedArk.salt.size)
    }

    @Test
    fun `persists biometric-wrapped ARK when cipher is provided`() = runTest {
        val biometricKek = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val biometricCipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.WRAP_MODE, biometricKek)
        }

        val result = useCase("password", biometricCipher = biometricCipher)

        assertTrue(result.isSuccess())
        val stored = accountRepository.getOrNull()!!
        val bio = assertNotNull(stored.biometricWrappedArk)
        assertTrue(bio.key.isNotEmpty())
        assertTrue(bio.keyIV.isNotEmpty())
    }

    @Test
    fun `does not persist biometric-wrapped ARK when no cipher provided`() = runTest {
        useCase("password", biometricCipher = null)

        assertEquals(null, accountRepository.getOrNull()?.biometricWrappedArk)
    }

    @Test
    fun `creates account and default vault with supplied name`() = runTest {
        useCase("password", vaultName = "My Vault")

        val vault = vaultRepository.observeVaults().first().single()
        assertEquals("My Vault", vault.name)
    }

    @Test
    fun `uses supplied account display name`() = runTest {
        useCase("password", accountDisplayName = "Work")

        assertEquals("Work", accountRepository.getOrNull()?.displayName)
    }

    @Test
    fun `generates different salts for different invocations`() = runTest {
        useCase("password")
        val salt1 = accountRepository.getOrNull()!!.passwordWrappedArk.salt.copyOf()

        useCase("password")
        val salt2 = accountRepository.getOrNull()!!.passwordWrappedArk.salt

        assertTrue(!salt1.contentEquals(salt2))
    }
}
