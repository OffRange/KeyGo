package de.davis.keygo.core.identity.domain.usecase

import de.davis.keygo.core.identity.FakeAccountRepository
import de.davis.keygo.core.identity.domain.model.CreateAccessError
import de.davis.keygo.core.item.FakeVaultContextRepository
import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.repository.VaultContextRepository
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.isSuccess
import de.davis.keygo.rust.FakeArkSession
import de.davis.keygo.rust.RecordingArkSession
import de.davisalessandro.keygo.rust.WrappedKeyBlob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CreateAccessUseCaseTest {

    private val arkSession = FakeArkSession()
    private val session = Session(arkSession)
    private val accountRepository = FakeAccountRepository()
    private val vaultRepository = FakeVaultRepository()
    private val vaultContextRepository = FakeVaultContextRepository()

    private val useCase = CreateAccessUseCase(
        accountRepository = accountRepository,
        vaultRepository = vaultRepository,
        vaultContextRepository = vaultContextRepository,
        session = session,
    )

    @Test
    fun `returns KeyDerivationFailed when derivation fails`() = runTest {
        arkSession.failDerivation = true

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
    fun `returns Success and leaves the session unlocked without biometric cipher`() = runTest {
        val result = useCase("password", biometricCipher = null)

        assertTrue(result.isSuccess())
        assertTrue(session.isActive.value)
        // The vault the use case persisted has to unwrap under the ARK the session now holds.
        val vault = vaultRepository.observeVaults().first().single()
        assertTrue(
            session.unwrapVaultKey(
                wrapped = WrappedKeyBlob(
                    ciphertext = vault.keyInformation.wrappedKey,
                    nonce = vault.keyInformation.keyNonce,
                ),
                vaultId = vault.id,
            ).isSuccess()
        )
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

    /**
     * The ARK reaches the JVM here only so a Keystore cipher can wrap it, and the `finally` that
     * zeroes it afterwards is the only thing keeping it from staying resident. [RecordingArkSession]
     * hands out the array itself rather than a copy, so the wipe is observable.
     */
    @Test
    fun `wipes the exported ARK after wrapping it for biometrics`() = runTest {
        val recording = RecordingArkSession(startUnlocked = true)
        val biometricKek = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val biometricCipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.WRAP_MODE, biometricKek)
        }

        useCaseOver(recording)("password", biometricCipher = biometricCipher)

        assertContentEquals(ByteArray(32), recording.onlyExported())
    }

    @Test
    fun `wipes the exported ARK even when wrapping fails`() = runTest {
        val recording = RecordingArkSession(startUnlocked = true)
        // A cipher in the wrong mode makes Cipher.wrap throw, so the wrap fails after the export.
        val kek = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val wrongMode = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, kek)
        }

        val result = useCaseOver(recording)("password", biometricCipher = wrongMode)

        assertTrue(result.isFailure())
        assertEquals(CreateAccessError.WrappingFailed, result.error)
        assertContentEquals(ByteArray(32), recording.onlyExported())
    }

    @Test
    fun `ends the session when account persistence fails`() = runTest {
        accountRepository.setFails = true

        useCase("password")

        // Nothing was persisted, so a retained ARK would be a key with nothing left to unwrap.
        assertFalse(session.isActive.value)
    }

    @Test
    fun `ends the session when vault persistence fails`() = runTest {
        vaultRepository.createError = RuntimeException("disk full")

        useCase("password")

        assertFalse(session.isActive.value)
    }

    @Test
    fun `ends the session when the last write throws`() = runTest {
        val throwing = CreateAccessUseCase(
            accountRepository = accountRepository,
            vaultRepository = vaultRepository,
            vaultContextRepository = ThrowingVaultContextRepository(),
            session = session,
        )

        assertFailsWith<RuntimeException> { throwing("password") }

        // The throw leaves `create` without a return value, so only a `finally` can hand back
        // the ARK. A guard on the result would let this path keep the key resident.
        assertFalse(session.isActive.value)
    }

    @Test
    fun `generates different salts for different invocations`() = runTest {
        useCase("password")
        val salt1 = accountRepository.getOrNull()!!.passwordWrappedArk.salt.copyOf()

        useCase("password")
        val salt2 = accountRepository.getOrNull()!!.passwordWrappedArk.salt

        assertTrue(!salt1.contentEquals(salt2))
    }

    private fun useCaseOver(arkSession: RecordingArkSession) = CreateAccessUseCase(
        accountRepository = accountRepository,
        vaultRepository = vaultRepository,
        vaultContextRepository = vaultContextRepository,
        session = Session(arkSession),
    )
}

/**
 * Throws on the last write the use case makes, which is the only step reached after both persists
 * have succeeded. None of the fakes throw, so the exception path out of `create` needs its own
 * stand-in to be observable at all.
 */
private class ThrowingVaultContextRepository(
    private val delegate: FakeVaultContextRepository = FakeVaultContextRepository(),
) : VaultContextRepository by delegate {

    override suspend fun setContextAndLastInteracted(vaultId: VaultId): Unit =
        throw RuntimeException("datastore gone")
}
