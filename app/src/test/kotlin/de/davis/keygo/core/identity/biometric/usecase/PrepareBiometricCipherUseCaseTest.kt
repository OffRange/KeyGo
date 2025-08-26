package de.davis.keygo.core.identity.biometric.usecase

import de.davis.keygo.core.domain.Result
import de.davis.keygo.core.domain.isFailure
import de.davis.keygo.core.domain.isSuccess
import de.davis.keygo.core.domain.model.crypto.asAesKey
import de.davis.keygo.core.identity.biometric.domain.model.KeyStoreError
import de.davis.keygo.core.identity.biometric.domain.repository.BiometricKekRepository
import de.davis.keygo.core.identity.biometric.domain.usecase.PrepareBiometricCipherUseCase
import de.davis.keygo.core.identity.common.domain.CipherFactory
import de.davis.keygo.core.identity.common.domain.model.BiometricWrappedKeyData
import de.davis.keygo.core.identity.common.domain.model.CryptographicMode
import de.davis.keygo.core.identity.common.domain.model.CryptographyError
import de.davis.keygo.core.identity.common.domain.repository.BiometricWrappedKeyRepository
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
    fun `test failing - unwrapping - without wrapped key data`() = runTest {
        val fakeKey = mockk<SecretKey>().asAesKey()
        every { kekRepository.getKek() } returns Result.Success(fakeKey)
        coEvery { biometricWrappedKeyRepository.getWrappedKeyData() } returns null
        val result = useCase(mode = CryptographicMode.Unwrap)

        assertTrue(result.isFailure())
        assertEquals(expected = CryptographyError.WrappedKeyNotFound, actual = result.error)
    }

    @Test
    fun `test succeeding - unwrapping - with kek`() = runTest {
        val fakeKey = mockk<SecretKey>().asAesKey()
        val data = BiometricWrappedKeyData(
            wrappedKey = byteArrayOf(),
            iv = byteArrayOf(0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8)
        )
        val fakeCipher = mockk<Cipher>()

        coEvery { biometricWrappedKeyRepository.getWrappedKeyData() } returns data
        every { kekRepository.getKek() } returns Result.Success(fakeKey)
        coEvery { cipherFactory.prepareCipher(any(), fakeKey, data.iv) } returns Result.Success(
            fakeCipher
        )

        val result = useCase(mode = CryptographicMode.Unwrap)

        assertTrue(result.isSuccess())
        assertEquals(expected = fakeCipher, actual = result.success)
    }

    @Test
    fun `test succeeding - unwrapping - without kek`() = runTest {
        val fakeKey = mockk<SecretKey>().asAesKey()
        val data = BiometricWrappedKeyData(
            wrappedKey = byteArrayOf(),
            iv = byteArrayOf(0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8)
        )
        val fakeCipher = mockk<Cipher>()

        coEvery { biometricWrappedKeyRepository.getWrappedKeyData() } returns data
        every { kekRepository.getKek() } returns Result.Failure(KeyStoreError.KeyNotFound)
        coEvery { kekRepository.createKek() } returns fakeKey
        coEvery { cipherFactory.prepareCipher(any(), fakeKey, data.iv) } returns Result.Success(
            fakeCipher
        )

        val result = useCase(mode = CryptographicMode.Unwrap)

        assertTrue(result.isSuccess())
        assertEquals(expected = fakeCipher, actual = result.success)
    }

    @Test
    fun `test succeeding - wrapping - with kek`() = runTest {
        val fakeKey = mockk<SecretKey>().asAesKey()
        val fakeCipher = mockk<Cipher>()

        every { kekRepository.getKek() } returns Result.Success(fakeKey)
        coEvery { cipherFactory.prepareCipher(any(), fakeKey, null) } returns Result.Success(
            fakeCipher
        )

        val result = useCase(mode = CryptographicMode.Wrap)

        assertTrue(result.isSuccess())
        assertEquals(expected = fakeCipher, actual = result.success)
    }

    @Test
    fun `test succeeding - wrapping - without kek`() = runTest {
        val fakeKey = mockk<SecretKey>().asAesKey()
        val fakeCipher = mockk<Cipher>()

        every { kekRepository.getKek() } returns Result.Failure(KeyStoreError.KeyNotFound)
        coEvery { kekRepository.createKek() } returns fakeKey
        coEvery { cipherFactory.prepareCipher(any(), fakeKey, null) } returns Result.Success(
            fakeCipher
        )

        val result = useCase(mode = CryptographicMode.Wrap)

        assertTrue(result.isSuccess())
        assertEquals(expected = fakeCipher, actual = result.success)
    }
}