package de.davis.keygo.auth.domain.usecase

import de.davis.keygo.auth.domain.factory.BiometricCipherFactory
import de.davis.keygo.auth.domain.model.BiometricWrappedKeyData
import de.davis.keygo.auth.domain.model.CryptographyError
import de.davis.keygo.auth.domain.repository.BiometricWrappedKeyRepository
import de.davis.keygo.core.domain.Result
import de.davis.keygo.core.domain.Session
import de.davis.keygo.core.domain.isFailure
import de.davis.keygo.core.domain.isSuccess
import de.davis.keygo.core.domain.model.crypto.asAesKey
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import javax.crypto.Cipher
import javax.crypto.SecretKey
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnlockWithBiometricsUseCaseTest {

    private lateinit var cipher: Cipher
    private lateinit var biometricCipherFactory: BiometricCipherFactory
    private lateinit var wrappedKeyRepository: BiometricWrappedKeyRepository
    private lateinit var session: Session
    private lateinit var useCase: UnlockWithBiometricsUseCase

    @BeforeTest
    fun setUp() {
        cipher = mockk()
        biometricCipherFactory = mockk()
        wrappedKeyRepository = mockk()
        session = mockk(relaxed = true)
        useCase = UnlockWithBiometricsUseCase(
            biometricCipherFactory = biometricCipherFactory,
            wrappedKeyRepository = wrappedKeyRepository,
            session = session
        )
    }

    @Test
    fun `test fail when has no wrapped key`() = runTest {
        coEvery { wrappedKeyRepository.getBiometricWrappedKeyData() } returns null
        val result = useCase(cipher)

        assertTrue(result.isFailure())
        assertEquals(expected = CryptographyError.WrappedKeyNotFound, actual = result.error)
    }

    @Test
    fun `test fail when unwrapping key fails`() = runTest {
        val error = mockk<CryptographyError>()
        coEvery { wrappedKeyRepository.getBiometricWrappedKeyData() } returns BiometricWrappedKeyData(
            wrappedKey = byteArrayOf(),
            iv = byteArrayOf()
        )
        coEvery {
            biometricCipherFactory.unwrapDataKey(
                cipher,
                any()
            )
        } returns Result.Failure(error)

        val result = useCase(cipher)

        assertTrue(result.isFailure())
        assertEquals(expected = error, actual = result.error)
    }

    @Test
    fun `test succeeds`() = runTest {
        val wrappedKeyData = BiometricWrappedKeyData(
            wrappedKey = byteArrayOf(),
            iv = byteArrayOf()
        )
        val aesKey = mockk<SecretKey>().asAesKey()
        coEvery { wrappedKeyRepository.getBiometricWrappedKeyData() } returns wrappedKeyData
        coEvery {
            biometricCipherFactory.unwrapDataKey(
                cipher,
                any()
            )
        } returns Result.Success(aesKey)

        val result = useCase(cipher)

        assertTrue(result.isSuccess())
        verify { session.startSession(aesKey) }
    }
}