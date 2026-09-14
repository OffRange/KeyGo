@file:OptIn(ExportArk::class)

package de.davis.keygo.core.identity.domain.usecase

import de.davis.keygo.core.biometrics.FakeBiometricCrypto
import de.davis.keygo.core.biometrics.domain.model.BiometricAuthError
import de.davis.keygo.core.biometrics.domain.model.BiometricString
import de.davis.keygo.core.identity.FakeAccountRepository
import de.davis.keygo.core.identity.domain.mapper.toBiometricWrappedArk
import de.davis.keygo.core.identity.domain.model.Account
import de.davis.keygo.core.identity.domain.model.ChangePasswordError
import de.davis.keygo.core.identity.domain.model.PasswordWrappedArk
import de.davis.keygo.core.identity.domain.model.Reauthentication
import de.davis.keygo.core.security.FakeSession
import de.davis.keygo.core.security.domain.ExportArk
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.util.assertSuccess
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.isSuccess
import de.davisalessandro.keygo.rust.NewAccount
import de.davisalessandro.keygo.rust.WrappedKeyBlob
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChangePasswordUseCaseTest {

    private val session = FakeSession()
    private val accountRepository = FakeAccountRepository()
    private val biometricCrypto = FakeBiometricCrypto()

    private val useCase = ChangePasswordUseCase(
        biometricCrypto = biometricCrypto,
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
        biometricArk: () -> ByteArray = ::liveArk,
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
                biometricCrypto
                    .requestWrap(KeyId.BiometricVaultKek) { seal -> seal(biometricArk()) }
                    .assertSuccess()
                    .toBiometricWrappedArk()
            } else null,
        )
        accountRepository.seed(account)
        biometricCrypto.prompts.clear()
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
        val probe = FakeSession()

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

    /**
     * The stored blob opens under the right password, but around a different key than the session
     * holds. Rewrapping would put the new password around the session's key, which the stored
     * account never had, so the next password unlock would open nothing.
     */
    @Test
    fun `returns IncorrectPassword when the stored ARK is not the one the session holds`() =
        runTest {
            seedAccount("old")
            session.unlockWithArk(ByteArray(32) { (it + 7).toByte() })

            val result = useCase(Reauthentication.Password("old"), "new")

            assertTrue(result.isFailure())
            assertEquals(ChangePasswordError.IncorrectPassword, result.error)
            assertTrue(unlocksWith("old"))
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
    fun `password path never shows a biometric prompt`() = runTest {
        seedAccount("old", withBiometric = true)

        useCase(Reauthentication.Password("old"), "new")

        assertTrue(biometricCrypto.prompts.isEmpty())
    }

    @Test
    fun `biometric path re-wraps the live ARK under the new password`() = runTest {
        seedAccount("old", withBiometric = true)

        val result = useCase(Reauthentication.Biometric, "new")

        assertTrue(result.isSuccess())
        assertTrue(unlocksWith("new"))
        assertFalse(unlocksWith("old"))
    }

    @Test
    fun `biometric path unwraps with the biometric key and offers the password as the way out`() =
        runTest {
            seedAccount("old", withBiometric = true)

            useCase(Reauthentication.Biometric, "new")

            val prompt = biometricCrypto.prompts.single()
            assertEquals(KeyId.BiometricVaultKek, prompt.keyId)
            assertEquals(CryptographicMode.Unwrap, prompt.mode)
            assertEquals(BiometricString.NegativeButton.Password, prompt.policy.negativeButton)
        }

    @Test
    fun `returns BiometricAuthFailed when the biometric ARK is not the live one`() = runTest {
        seedAccount("old", withBiometric = true) { ByteArray(32) { it.toByte() } }

        val result = useCase(Reauthentication.Biometric, "new")

        assertTrue(result.isFailure())
        assertEquals(ChangePasswordError.BiometricAuthFailed, result.error)
        assertTrue(unlocksWith("old"))
    }

    @Test
    fun `biometric path on a locked session fails as ActiveAccountNotFound`() = runTest {
        seedAccount("old", withBiometric = true)
        session.endSession()

        val result = useCase(Reauthentication.Biometric, "new")

        assertTrue(result.isFailure())
        assertEquals(ChangePasswordError.ActiveAccountNotFound, result.error)
    }

    @Test
    fun `returns BiometricNotEnrolled without prompting when none is enrolled`() = runTest {
        seedAccount("old", withBiometric = false)

        val result = useCase(Reauthentication.Biometric, "new")

        assertTrue(result.isFailure())
        assertEquals(ChangePasswordError.BiometricNotEnrolled, result.error)
        assertTrue(biometricCrypto.prompts.isEmpty())
    }

    @Test
    fun `a declined prompt is reported apart so the screen can ask for the password`() = runTest {
        seedAccount("old", withBiometric = true)
        biometricCrypto.promptFailure = BiometricAuthError.Declined

        val result = useCase(Reauthentication.Biometric, "new")

        assertTrue(result.isFailure())
        assertEquals(ChangePasswordError.BiometricDeclined, result.error)
        assertTrue(unlocksWith("old"))
    }

    @Test
    fun `a canceled prompt is reported apart so the screen can stay quiet`() = runTest {
        seedAccount("old", withBiometric = true)
        biometricCrypto.promptFailure = BiometricAuthError.Canceled

        val result = useCase(Reauthentication.Biometric, "new")

        assertTrue(result.isFailure())
        assertEquals(ChangePasswordError.BiometricCanceled, result.error)
    }

    @Test
    fun `a prompt that fails on its own is BiometricAuthFailed`() = runTest {
        seedAccount("old", withBiometric = true)
        biometricCrypto.promptFailure = BiometricAuthError.LockedOut

        val result = useCase(Reauthentication.Biometric, "new")

        assertTrue(result.isFailure())
        assertEquals(ChangePasswordError.BiometricAuthFailed, result.error)
        assertTrue(unlocksWith("old"))
    }

    @Test
    fun `an invalidated biometric key is BiometricAuthFailed`() = runTest {
        seedAccount("old", withBiometric = true)
        biometricCrypto.keyStoreManager.deleteKey(KeyId.BiometricVaultKek)

        val result = useCase(Reauthentication.Biometric, "new")

        assertTrue(result.isFailure())
        assertEquals(ChangePasswordError.BiometricAuthFailed, result.error)
    }

    @Test
    fun `returns KeyDerivationFailed when derivation fails`() = runTest {
        seedAccount("old")
        session.failDerivation = true

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

    /**
     * Changing a password needs the live ARK, and proving the current password compares against
     * it, so a locked session fails at reauthentication. It is reported as the missing session it
     * is, not as a wrong password.
     */
    @Test
    fun `change password fails as ActiveAccountNotFound when the session is locked`() = runTest {
        seedAccount("old")
        session.endSession()

        val result = useCase(Reauthentication.Password("old"), "new")

        assertTrue(result.isFailure())
        assertEquals(ChangePasswordError.ActiveAccountNotFound, result.error)
    }

    @Test
    fun `scrubs the unwrapped biometric ARK after a successful change`() = runTest {
        seedAccount("old", withBiometric = true)

        useCase(Reauthentication.Biometric, "new")

        assertContentEquals(ByteArray(32), biometricCrypto.unwrapped.single())
    }

    @Test
    fun `scrubs the unwrapped biometric ARK when persistence fails`() = runTest {
        seedAccount("old", withBiometric = true)
        accountRepository.setFails = true

        useCase(Reauthentication.Biometric, "new")

        assertContentEquals(ByteArray(32), biometricCrypto.unwrapped.single())
    }

    @Test
    fun `scrubs the unwrapped biometric ARK when it is not the live one`() = runTest {
        seedAccount("old", withBiometric = true) { ByteArray(32) { it.toByte() } }

        useCase(Reauthentication.Biometric, "new")

        assertContentEquals(ByteArray(32), biometricCrypto.unwrapped.single())
    }

    @Test
    fun `scrubs the unwrapped biometric ARK when the session is locked`() = runTest {
        seedAccount("old", withBiometric = true)
        session.endSession()

        useCase(Reauthentication.Biometric, "new")

        assertContentEquals(ByteArray(32), biometricCrypto.unwrapped.single())
    }
}
