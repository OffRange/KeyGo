package de.davis.keygo.core.identity.domain.usecase

import de.davis.keygo.core.identity.FakeAccountRepository
import de.davis.keygo.core.identity.domain.model.Account
import de.davis.keygo.core.identity.domain.model.BiometricWrappedArk
import de.davis.keygo.core.identity.domain.model.ChangePasswordError
import de.davis.keygo.core.identity.domain.model.PasswordWrappedArk
import de.davis.keygo.core.identity.domain.model.Reauthentication
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.isSuccess
import de.davis.keygo.rust.FakeArkSession
import de.davisalessandro.keygo.rust.NewAccount
import de.davisalessandro.keygo.rust.WrappedKeyBlob
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChangePasswordUseCaseTest {

    private val arkSession = FakeArkSession()
    private val session = Session(arkSession)
    private val accountRepository = FakeAccountRepository()

    private val useCase = ChangePasswordUseCase(
        accountRepository = accountRepository,
        session = session,
    )

    /** What the session minted for the seeded account, for round-trip assertions. */
    private lateinit var created: NewAccount

    /**
     * Mints an account through the session and persists it. The session stays unlocked, which is
     * what the change-password screen guarantees.
     */
    private suspend fun seedAccount(
        password: String,
        withBiometric: Boolean = false,
    ): Account {
        created = checkNotNull(session.createAccount(password).getOrNull())

        val account = Account(
            id = created.userId,
            displayName = "Test",
            passwordWrappedArk = PasswordWrappedArk(
                key = created.passwordWrappedArk.ciphertext,
                keyIV = created.passwordWrappedArk.nonce,
                salt = created.salt,
            ),
            biometricWrappedArk = if (withBiometric) {
                BiometricWrappedArk(
                    key = ByteArray(48) { it.toByte() },
                    keyIV = ByteArray(12) { it.toByte() },
                )
            } else null,
        )
        accountRepository.seed(account)
        return account
    }

    /** The live ARK, which the biometric path has to hand back to prove reauthentication. */
    private fun liveArk(): ByteArray = checkNotNull(session.exportArk().getOrNull())

    /**
     * Whether the stored password-wrapped ARK opens under [password], in a session that shares no
     * state with the one under test. It is the same ARK, not merely a well-formed one, when the
     * default vault key minted alongside the account still unwraps in that fresh session.
     */
    private suspend fun unlocksWith(password: String): Boolean {
        val stored = accountRepository.getOrNull()!!.passwordWrappedArk
        val probe = Session(FakeArkSession())

        val unlocked = probe.unlockWithPassword(
            password = password,
            salt = stored.salt,
            wrapped = WrappedKeyBlob(ciphertext = stored.key, nonce = stored.keyIV),
            userId = created.userId,
        )
        if (unlocked.isFailure()) return false

        return probe.unwrapVaultKey(created.wrappedVaultKey, created.vaultId).isSuccess()
    }

    @Test
    fun `returns ActiveAccountNotFound when no account is registered`() = runTest {
        val result = useCase(Reauthentication.Password("old"), "new")

        assertTrue(result.isFailure())
        assertEquals(ChangePasswordError.ActiveAccountNotFound, result.error)
    }

    @Test
    fun `returns IncorrectPassword when current password is wrong`() = runTest {
        seedAccount("old")

        val result = useCase(Reauthentication.Password("wrong"), "new")

        assertTrue(result.isFailure())
        assertEquals(ChangePasswordError.IncorrectPassword, result.error)
    }

    @Test
    fun `password path re-wraps ARK so new password unwraps and old fails`() = runTest {
        seedAccount("old")

        val result = useCase(Reauthentication.Password("old"), "new")

        assertTrue(result.isSuccess())
        assertTrue(unlocksWith("new"))
        assertFalse(unlocksWith("old"))
    }

    @Test
    fun `password change rotates the salt`() = runTest {
        val before = seedAccount("old").passwordWrappedArk.salt.copyOf()

        useCase(Reauthentication.Password("old"), "new")

        val after = accountRepository.getOrNull()!!.passwordWrappedArk.salt
        assertFalse(before.contentEquals(after))
    }

    @Test
    fun `password change leaves the biometric-wrapped ARK untouched`() = runTest {
        val before = seedAccount("old", withBiometric = true).biometricWrappedArk!!

        useCase(Reauthentication.Password("old"), "new")

        val after = accountRepository.getOrNull()!!.biometricWrappedArk!!
        assertContentEquals(before.key, after.key)
        assertContentEquals(before.keyIV, after.keyIV)
    }

    @Test
    fun `biometric path re-wraps the live ARK under the new password`() = runTest {
        seedAccount("old", withBiometric = true)

        val result = useCase(Reauthentication.Biometric(liveArk()), "new")

        assertTrue(result.isSuccess())
        assertTrue(unlocksWith("new"))
    }

    @Test
    fun `returns IncorrectPassword when the biometric ARK is not the live one`() = runTest {
        seedAccount("old", withBiometric = true)

        val result = useCase(Reauthentication.Biometric(ByteArray(32) { it.toByte() }), "new")

        assertTrue(result.isFailure())
        assertEquals(ChangePasswordError.IncorrectPassword, result.error)
    }

    @Test
    fun `returns BiometricNotEnrolled when biometric proof given but none enrolled`() = runTest {
        seedAccount("old", withBiometric = false)

        val result = useCase(Reauthentication.Biometric(liveArk()), "new")

        assertTrue(result.isFailure())
        assertEquals(ChangePasswordError.BiometricNotEnrolled, result.error)
    }

    @Test
    fun `returns KeyDerivationFailed when derivation fails`() = runTest {
        seedAccount("old")
        arkSession.failDerivation = true

        val result = useCase(Reauthentication.Password("old"), "new")

        assertTrue(result.isFailure())
        assertEquals(ChangePasswordError.KeyDerivationFailed, result.error)
    }

    @Test
    fun `returns PersistenceFailed when the account cannot be saved`() = runTest {
        seedAccount("old")
        accountRepository.setFails = true

        val result = useCase(Reauthentication.Password("old"), "new")

        assertTrue(result.isFailure())
        assertEquals(ChangePasswordError.PersistenceFailed, result.error)
    }

    @Test
    fun `change password fails when the session is locked`() = runTest {
        seedAccount("old")
        session.endSession()

        val result = useCase(Reauthentication.Password("old"), "new")

        assertTrue(result.isFailure())
    }

    @Test
    fun `scrubs the supplied biometric ARK after a successful change`() = runTest {
        seedAccount("old", withBiometric = true)
        val recovered = liveArk()

        useCase(Reauthentication.Biometric(recovered), "new")

        assertContentEquals(ByteArray(recovered.size), recovered)
    }

    @Test
    fun `scrubs the supplied biometric ARK when persistence fails`() = runTest {
        seedAccount("old", withBiometric = true)
        val recovered = liveArk()
        accountRepository.setFails = true

        useCase(Reauthentication.Biometric(recovered), "new")

        assertContentEquals(ByteArray(recovered.size), recovered)
    }

    @Test
    fun `scrubs the supplied biometric ARK when biometric reauth is not enrolled`() = runTest {
        seedAccount("old", withBiometric = false)
        val recovered = liveArk()

        useCase(Reauthentication.Biometric(recovered), "new")

        assertContentEquals(ByteArray(recovered.size), recovered)
    }
}
