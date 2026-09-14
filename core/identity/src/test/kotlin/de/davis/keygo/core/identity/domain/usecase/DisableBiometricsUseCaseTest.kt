package de.davis.keygo.core.identity.domain.usecase

import de.davis.keygo.core.biometrics.FakeBiometricCrypto
import de.davis.keygo.core.biometrics.domain.model.BiometricAuthError
import de.davis.keygo.core.biometrics.domain.model.BiometricEnrollmentError
import de.davis.keygo.core.identity.FakeAccountRepository
import de.davis.keygo.core.identity.domain.mapper.toBiometricWrappedArk
import de.davis.keygo.core.identity.domain.model.Account
import de.davis.keygo.core.identity.domain.model.PasswordWrappedArk
import de.davis.keygo.core.security.crypto.FakeKeyStoreManager
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.core.security.domain.model.CryptographicMode
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.util.assertFailure
import de.davis.keygo.core.util.assertSuccess
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DisableBiometricsUseCaseTest {

    private val accountRepository = FakeAccountRepository()
    private val keyStoreManager = FakeKeyStoreManager()
    private val biometricCrypto = FakeBiometricCrypto(keyStoreManager)

    private val disableBiometrics = DisableBiometricsUseCase(
        accountRepository = accountRepository,
        keyStoreManager = keyStoreManager,
    )

    private suspend fun seedEnrolledAccount() {
        accountRepository.seed(
            Account(
                id = UUID.randomUUID(),
                displayName = "Test",
                passwordWrappedArk = PasswordWrappedArk(
                    key = ByteArray(48) { 1 },
                    keyIV = ByteArray(12) { 2 },
                    salt = ByteArray(16) { 3 },
                ),
                biometricWrappedArk = biometricCrypto
                    .requestWrap(KeyId.BiometricVaultKek, ByteArray(32) { 4 })
                    .assertSuccess()
                    .toBiometricWrappedArk(),
            ),
        )
        biometricCrypto.prompts.clear()
    }

    @Test
    fun `disabling drops the wrapped ARK and the keystore alias behind it`() = runTest {
        seedEnrolledAccount()

        disableBiometrics().assertSuccess()

        assertNull(accountRepository.getOrNull()?.biometricWrappedArk)
        assertFalse(KeyId.BiometricVaultKek in keyStoreManager.keys)
    }

    @Test
    fun `a wrapped ARK captured before disabling no longer opens`() = runTest {
        seedEnrolledAccount()
        val captured = accountRepository.getOrNull()!!.biometricWrappedArk!!

        disableBiometrics()

        val error = biometricCrypto.requestUnwrap(
            keyId = KeyId.BiometricVaultKek,
            cryptographicData = CryptographicData(data = captured.key, iv = captured.keyIV),
        ).assertFailure()
        assertEquals(BiometricAuthError.KeyInvalidated, error)
    }

    @Test
    fun `disabling never shows a prompt`() = runTest {
        seedEnrolledAccount()

        disableBiometrics()

        assertTrue(biometricCrypto.prompts.isEmpty())
    }

    @Test
    fun `disabling leaves the backup escrow aliases alone`() = runTest {
        seedEnrolledAccount()
        keyStoreManager.getOrCreateCipherFor(KeyId.BackupArkKey, CryptographicMode.Encrypt)
        keyStoreManager.getOrCreateCipherFor(KeyId.BackupPassphraseKey, CryptographicMode.Encrypt)

        disableBiometrics()

        assertTrue(KeyId.BackupArkKey in keyStoreManager.keys)
        assertTrue(KeyId.BackupPassphraseKey in keyStoreManager.keys)
    }

    @Test
    fun `a failed clear keeps the key that the stored enrollment still needs`() = runTest {
        seedEnrolledAccount()
        accountRepository.setFails = true

        val error = disableBiometrics().assertFailure()

        assertEquals(BiometricEnrollmentError.PersistenceFailed, error)
        assertNotNull(accountRepository.getOrNull()?.biometricWrappedArk)
        assertTrue(KeyId.BiometricVaultKek in keyStoreManager.keys)
    }

    @Test
    fun `disabling without an account touches nothing`() = runTest {
        keyStoreManager.getOrCreateCipherFor(KeyId.BiometricVaultKek, CryptographicMode.Wrap)

        val error = disableBiometrics().assertFailure()

        assertEquals(BiometricEnrollmentError.NoActiveAccount, error)
        assertTrue(KeyId.BiometricVaultKek in keyStoreManager.keys)
    }
}
