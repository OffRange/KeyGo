package de.davis.keygo.auth.domain.usecase

import de.davis.keygo.auth.data.repository.BiometricWrappedKeyRepository
import de.davis.keygo.auth.domain.factory.CipherFactory
import de.davis.keygo.auth.domain.model.BiometricWrappedKeyData
import de.davis.keygo.auth.domain.model.CryptographicMode
import de.davis.keygo.auth.domain.model.CryptographyError
import de.davis.keygo.auth.domain.model.KeyStoreError
import de.davis.keygo.auth.domain.repository.BiometricKekRepository
import de.davis.keygo.core.domain.Result
import de.davis.keygo.core.domain.isFailure
import de.davis.keygo.core.domain.isSuccess
import de.davis.keygo.core.domain.model.crypto.asAesKey
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import javax.crypto.Cipher
import javax.crypto.SecretKey
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrepareBiometricCipherUseCaseTest {

    private lateinit var kekRepository: BiometricKekRepository
    private lateinit var biometricWrappedKeyRepository: BiometricWrappedKeyRepository
    private lateinit var cipherFactory: CipherFactory

    private lateinit var useCase: PrepareBiometricCipherUseCase

    @BeforeTest
    fun setUp() {
        kekRepository = mockk()
        biometricWrappedKeyRepository = mockk()
        cipherFactory = mockk()

        useCase = PrepareBiometricCipherUseCase(
            kekRepository = kekRepository,
            biometricWrappedKeyRepository = biometricWrappedKeyRepository,
            cipherFactory = cipherFactory
        )
    }

    @Test
    fun `test return Failure when no kek was found`() = runTest {
        every { kekRepository.getKek() } returns Result.Failure(KeyStoreError.KeyNotFound)
        val result = useCase(mode = CryptographicMode.Wrap)

        assertTrue(result.isFailure())
        assertEquals(expected = CryptographyError.KeyNotInKeyStore, actual = result.error)
    }

    @Test
    fun `test return Failure when no wrapped key was found`() = runTest {
        val fakeKey = mockk<SecretKey>().asAesKey()
        every { kekRepository.getKek() } returns Result.Success(fakeKey)
        coEvery { biometricWrappedKeyRepository.getWrappedKeyData() } returns null
        val result = useCase(mode = CryptographicMode.Wrap)

        assertTrue(result.isFailure())
        assertEquals(expected = CryptographyError.WrappedKeyNotFound, actual = result.error)
    }

    @Test
    fun `test return Success when kek and wrapped key was found`() = runTest {
        val fakeKey = mockk<SecretKey>().asAesKey()
        every { kekRepository.getKek() } returns Result.Success(fakeKey)
        coEvery { biometricWrappedKeyRepository.getWrappedKeyData() } returns BiometricWrappedKeyData(
            wrappedKey = byteArrayOf(),
            iv = byteArrayOf()
        )

        val fakeCipher = mockk<Cipher>()
        every { cipherFactory.prepareCipher(any(), any(), any()) } returns Result.Success(
            fakeCipher
        )

        val result = useCase(mode = CryptographicMode.Wrap)

        assertTrue(result.isSuccess())
        assertEquals(expected = fakeCipher, actual = result.success)
    }
}