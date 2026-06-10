package de.davis.keygo.core.identity.domain.usecase

import de.davis.keygo.core.identity.FakeAccountRepository
import de.davis.keygo.core.identity.domain.model.Account
import de.davis.keygo.core.identity.domain.model.BiometricWrappedArk
import de.davis.keygo.core.identity.domain.model.ChangePasswordError
import de.davis.keygo.core.identity.domain.model.PasswordWrappedArk
import de.davis.keygo.core.identity.domain.model.Reauthentication
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.isSuccess
import de.davis.keygo.rust.FakeKeyDeriver
import de.davis.keygo.rust.FakeKeyWrapper
import de.davisalessandro.keygo.rust.WrappedKeyBlob
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChangePasswordUseCaseTest {

    private val accountRepository = FakeAccountRepository()
    private val keyDeriver = FakeKeyDeriver()
    private val keyWrapper = FakeKeyWrapper()

    private val useCase = ChangePasswordUseCase(
        accountRepository = accountRepository,
        keyDeriver = keyDeriver,
        keyWrapper = keyWrapper,
    )

    private val accountId = UUID.randomUUID()
    private val ark = ByteArray(32) { (it + 1).toByte() }

    private fun seedAccount(
        password: String,
        withBiometric: Boolean = false,
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
            biometricWrappedArk = if (withBiometric) {
                BiometricWrappedArk(key = ByteArray(48) { it.toByte() }, keyIV = ByteArray(12) { it.toByte() })
            } else null,
        )
        accountRepository.seed(account)
        return account
    }

    /** Unwraps the stored password-wrapped ARK with [password]; returns null if it doesn't unwrap. */
    private suspend fun unwrapStoredArkWith(password: String): ByteArray? {
        val stored = accountRepository.getOrNull()!!.passwordWrappedArk
        val kek = keyDeriver.deriveRootKekFromPassword(password, stored.salt)
        return runCatching {
            keyWrapper.unwrapAccountRootKey(
                kek = kek,
                wrapped = WrappedKeyBlob(ciphertext = stored.key, nonce = stored.keyIV),
                userId = accountId,
            )
        }.getOrNull()
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
        assertContentEquals(ark, unwrapStoredArkWith("new"))
        assertEquals(null, unwrapStoredArkWith("old"))
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
    fun `biometric path re-wraps the supplied ARK under the new password`() = runTest {
        seedAccount("old", withBiometric = true)

        val result = useCase(Reauthentication.Biometric(ark.copyOf()), "new")

        assertTrue(result.isSuccess())
        assertContentEquals(ark, unwrapStoredArkWith("new"))
    }

    @Test
    fun `returns BiometricNotEnrolled when biometric proof given but none enrolled`() = runTest {
        seedAccount("old", withBiometric = false)

        val result = useCase(Reauthentication.Biometric(ark.copyOf()), "new")

        assertTrue(result.isFailure())
        assertEquals(ChangePasswordError.BiometricNotEnrolled, result.error)
    }

    @Test
    fun `returns KeyDerivationFailed when derivation fails`() = runTest {
        seedAccount("old")
        keyDeriver.failDerivation = true

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
    fun `scrubs the supplied biometric ARK after a successful change`() = runTest {
        seedAccount("old", withBiometric = true)
        val recovered = ark.copyOf()

        useCase(Reauthentication.Biometric(recovered), "new")

        assertContentEquals(ByteArray(recovered.size), recovered)
    }

    @Test
    fun `scrubs the supplied biometric ARK when persistence fails`() = runTest {
        seedAccount("old", withBiometric = true)
        accountRepository.setFails = true
        val recovered = ark.copyOf()

        useCase(Reauthentication.Biometric(recovered), "new")

        assertContentEquals(ByteArray(recovered.size), recovered)
    }

    @Test
    fun `scrubs the supplied biometric ARK when biometric reauth is not enrolled`() = runTest {
        seedAccount("old", withBiometric = false)
        val recovered = ark.copyOf()

        useCase(Reauthentication.Biometric(recovered), "new")

        assertContentEquals(ByteArray(recovered.size), recovered)
    }
}
