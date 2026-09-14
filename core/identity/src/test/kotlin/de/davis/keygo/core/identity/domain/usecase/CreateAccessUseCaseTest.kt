package de.davis.keygo.core.identity.domain.usecase

import de.davis.keygo.core.biometrics.FakeBiometricCrypto
import de.davis.keygo.core.biometrics.domain.model.BiometricAuthError
import de.davis.keygo.core.biometrics.domain.model.BiometricPolicy
import de.davis.keygo.core.biometrics.domain.model.BiometricString
import de.davis.keygo.core.identity.FakeAccountRepository
import de.davis.keygo.core.identity.domain.model.CreateAccessError
import de.davis.keygo.core.item.FakeVaultContextRepository
import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.repository.VaultContextRepository
import de.davis.keygo.core.security.FakeSession
import de.davis.keygo.core.security.crypto.FakeKeyStoreManager
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.security.domain.model.KeyStoreManagerError
import de.davis.keygo.core.util.assertSuccess
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.isSuccess
import de.davisalessandro.keygo.rust.WrappedKeyBlob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CreateAccessUseCaseTest {

    private val session = FakeSession()
    private val accountRepository = FakeAccountRepository()
    private val vaultRepository = FakeVaultRepository()
    private val vaultContextRepository = FakeVaultContextRepository()
    private val biometricCrypto = FakeBiometricCrypto()

    private val useCase = CreateAccessUseCase(
        accountRepository = accountRepository,
        vaultRepository = vaultRepository,
        vaultContextRepository = vaultContextRepository,
        biometricCrypto = biometricCrypto,
        session = session,
    )

    @Test
    fun `returns KeyDerivationFailed when derivation fails`() = runTest {
        session.failDerivation = true

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
    fun `returns Success and leaves the session unlocked without biometrics`() = runTest {
        val result = useCase("password", withBiometrics = false)

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
    fun `persists biometric-wrapped ARK when biometrics are requested`() = runTest {
        val result = useCase("password", withBiometrics = true)

        assertTrue(result.isSuccess())
        val stored = accountRepository.getOrNull()!!
        val bio = assertNotNull(stored.biometricWrappedArk)
        assertTrue(bio.key.isNotEmpty())
        assertTrue(bio.keyIV.isNotEmpty())
    }

    @Test
    fun `the biometric-wrapped ARK opens back to the ARK the session holds`() = runTest {
        useCase("password", withBiometrics = true)

        val bio = accountRepository.getOrNull()!!.biometricWrappedArk!!
        val recovered = biometricCrypto.requestUnwrap(
            keyId = KeyId.BiometricVaultKek,
            cryptographicData = CryptographicData(data = bio.key, iv = bio.keyIV),
        ).assertSuccess().encoded

        assertEquals(true, session.verifyArk(recovered).assertSuccess())
    }

    @Test
    fun `wraps under the biometric key with the policy it was given`() = runTest {
        val policy = BiometricPolicy(negativeButton = BiometricString.NegativeButton.Password)

        useCase("password", withBiometrics = true, policy = policy)

        val prompt = biometricCrypto.prompts.single()
        assertEquals(KeyId.BiometricVaultKek, prompt.keyId)
        assertEquals(CryptographicMode.Wrap, prompt.mode)
        assertEquals(policy, prompt.policy)
    }

    @Test
    fun `does not persist biometric-wrapped ARK or prompt when biometrics are not requested`() =
        runTest {
            useCase("password", withBiometrics = false)

            assertNull(accountRepository.getOrNull()?.biometricWrappedArk)
            assertTrue(biometricCrypto.prompts.isEmpty())
        }

    @Test
    fun `a failed biometric prompt reports WrappingFailed and persists nothing`() = runTest {
        biometricCrypto.promptFailure = BiometricAuthError.Declined

        val result = useCase("password", withBiometrics = true)

        assertTrue(result.isFailure())
        assertEquals(CreateAccessError.WrappingFailed, result.error)
        assertNull(accountRepository.getOrNull())
        assertTrue(vaultRepository.observeVaults().first().isEmpty())
        assertFalse(session.isActive.value)
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
     * zeroes it afterwards is the only thing keeping it from staying resident. [FakeSession] hands
     * out the array itself rather than a copy, so the wipe is observable.
     */
    @Test
    fun `wipes the exported ARK after wrapping it for biometrics`() = runTest {
        val recording = FakeSession(startUnlocked = true)

        useCaseOver(recording)("password", withBiometrics = true)

        assertContentEquals(ByteArray(32), recording.onlyExported())
    }

    @Test
    fun `wipes the exported ARK even when wrapping fails`() = runTest {
        val recording = FakeSession(startUnlocked = true)
        val refusing = FakeBiometricCrypto(
            keyStoreManager = FakeKeyStoreManager(failure = KeyStoreManagerError.Unknown),
        )

        val result = useCaseOver(recording, refusing)("password", withBiometrics = true)

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
            biometricCrypto = biometricCrypto,
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

    private fun useCaseOver(
        session: FakeSession,
        biometricCrypto: FakeBiometricCrypto = this.biometricCrypto,
    ) = CreateAccessUseCase(
        accountRepository = accountRepository,
        vaultRepository = vaultRepository,
        vaultContextRepository = vaultContextRepository,
        biometricCrypto = biometricCrypto,
        session = session,
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
