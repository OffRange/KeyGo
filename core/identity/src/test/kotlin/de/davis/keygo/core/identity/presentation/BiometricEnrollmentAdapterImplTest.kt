package de.davis.keygo.core.identity.presentation

import de.davis.keygo.core.identity.FakeAccountRepository
import de.davis.keygo.core.identity.domain.model.Account
import de.davis.keygo.core.identity.domain.model.BiometricEnrollmentError
import de.davis.keygo.core.identity.domain.model.BiometricWrappedArk
import de.davis.keygo.core.identity.domain.model.PasswordWrappedArk
import de.davis.keygo.core.security.crypto.FakeBiometricCryptoController
import de.davis.keygo.core.security.crypto.FakeKeyStoreManager
import de.davis.keygo.core.security.crypto.FakeSession
import de.davis.keygo.core.security.domain.model.BiometricAuthError
import de.davis.keygo.core.security.domain.model.BiometricPolicy
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.isSuccess
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BiometricEnrollmentAdapterImplTest {

    private val session = FakeSession()
    private val accountRepository = FakeAccountRepository()
    private val keyStoreManager = FakeKeyStoreManager()
    private val controller = FakeBiometricCryptoController()

    private val adapter = BiometricEnrollmentAdapterImpl(
        accountRepository = accountRepository,
        session = session,
        keyStoreManager = keyStoreManager,
    )

    private fun seedEnrolledAccount() {
        keyStoreManager.getOrCreateCipherFor(KeyId.BiometricVaultKek, CryptographicMode.Wrap)
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
    fun `disabling drops the wrapped ARK and the keystore alias behind it`() = runTest {
        seedEnrolledAccount()

        val result = adapter.disableBiometric()

        assertTrue(result.isSuccess())
        assertNull(accountRepository.getOrNull()?.biometricWrappedArk)
        assertFalse(KeyId.BiometricVaultKek in keyStoreManager.keys)
    }

    @Test
    fun `disabling leaves the backup escrow aliases alone`() = runTest {
        seedEnrolledAccount()
        keyStoreManager.getOrCreateCipherFor(KeyId.BackupArkKey, CryptographicMode.Encrypt)
        keyStoreManager.getOrCreateCipherFor(KeyId.BackupPassphraseKey, CryptographicMode.Encrypt)

        adapter.disableBiometric()

        assertTrue(KeyId.BackupArkKey in keyStoreManager.keys)
        assertTrue(KeyId.BackupPassphraseKey in keyStoreManager.keys)
    }

    @Test
    fun `a failed clear keeps the key that the stored enrollment still needs`() = runTest {
        seedEnrolledAccount()
        accountRepository.setFails = true

        val result = adapter.disableBiometric()

        assertTrue(result.isFailure())
        assertEquals(BiometricEnrollmentError.PersistenceFailed, result.error)
        assertNotNull(accountRepository.getOrNull()?.biometricWrappedArk)
        assertTrue(KeyId.BiometricVaultKek in keyStoreManager.keys)
    }

    @Test
    fun `a failed enrollment leaves the stored enrollment intact`() = runTest {
        seedEnrolledAccount()

        val result = with(adapter) { controller.requestEnableBiometric(BiometricPolicy.Default) }

        assertTrue(result.isFailure())
        assertEquals(
            BiometricEnrollmentError.BiometricFailed(BiometricAuthError.NoCipher),
            result.error,
        )
        assertTrue(KeyId.BiometricVaultKek in keyStoreManager.keys)
        assertNotNull(accountRepository.getOrNull()?.biometricWrappedArk)
    }

    @Test
    fun `enrolling without an account touches nothing`() = runTest {
        keyStoreManager.getOrCreateCipherFor(KeyId.BiometricVaultKek, CryptographicMode.Wrap)

        val result = with(adapter) { controller.requestEnableBiometric(BiometricPolicy.Default) }

        assertTrue(result.isFailure())
        assertEquals(BiometricEnrollmentError.NoActiveAccount, result.error)
        assertTrue(KeyId.BiometricVaultKek in keyStoreManager.keys)
    }

    @Test
    fun `disabling without an account touches nothing`() = runTest {
        keyStoreManager.getOrCreateCipherFor(KeyId.BiometricVaultKek, CryptographicMode.Wrap)

        val result = adapter.disableBiometric()

        assertTrue(result.isFailure())
        assertEquals(BiometricEnrollmentError.NoActiveAccount, result.error)
        assertTrue(KeyId.BiometricVaultKek in keyStoreManager.keys)
    }
}
