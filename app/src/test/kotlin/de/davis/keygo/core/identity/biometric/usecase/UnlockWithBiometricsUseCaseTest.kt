package de.davis.keygo.core.identity.biometric.usecase

import de.davis.keygo.core.domain.Result
import de.davis.keygo.core.domain.Session
import de.davis.keygo.core.domain.isFailure
import de.davis.keygo.core.domain.isSuccess
import de.davis.keygo.core.domain.model.crypto.asAesKey
import de.davis.keygo.core.identity.biometric.domain.usecase.UnlockWithBiometricsUseCase
import de.davis.keygo.core.identity.common.domain.CipherFactory
import de.davis.keygo.core.identity.common.domain.model.BiometricWrappedKeyData
import de.davis.keygo.core.identity.common.domain.model.CryptographyError
import de.davis.keygo.core.identity.common.domain.repository.BiometricWrappedKeyRepository
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
    private lateinit var cipherFactory: CipherFactory
    private lateinit var wrappedKeyRepository: BiometricWrappedKeyRepository
    private lateinit var session: Session
    private lateinit var useCase: UnlockWithBiometricsUseCase

    @BeforeTest
    fun setUp() {
        cipher = mockk()
        cipherFactory = mockk()
        wrappedKeyRepository = mockk()
        session = mockk(relaxed = true)
        useCase = UnlockWithBiometricsUseCase(
            cipherFactory = cipherFactory,
            wrappedKeyRepository = wrappedKeyRepository,
            session = session
        )
    }

    @Test
    fun `test failing - without wrapped key data`() = runTest {
        coEvery { wrappedKeyRepository.getWrappedKeyData() } returns null
        val result = useCase(cipher)

        assertTrue(result.isFailure())
        assertEquals(expected = CryptographyError.WrappedKeyNotFound, actual = result.error)
    }

    @Test
    fun `test failing - unwrapping key fails`() = runTest {
        val error = mockk<CryptographyError>()
        coEvery { wrappedKeyRepository.getWrappedKeyData() } returns BiometricWrappedKeyData(
            wrappedKey = byteArrayOf(),
            iv = byteArrayOf()
        )
        coEvery {
            cipherFactory.unwrapDataKey(
                cipher,
                any()
            )
        } returns Result.Failure(error)

        val result = useCase(cipher)

        assertTrue(result.isFailure())
        assertEquals(expected = error, actual = result.error)
    }

    @Test
    fun `test succeeding`() = runTest {
        val wrappedKeyData = BiometricWrappedKeyData(
            wrappedKey = byteArrayOf(),
            iv = byteArrayOf()
        )
        val aesKey = mockk<SecretKey>().asAesKey()
        coEvery { wrappedKeyRepository.getWrappedKeyData() } returns wrappedKeyData
        coEvery {
            cipherFactory.unwrapDataKey(
                cipher,
                any()
            )
        } returns Result.Success(aesKey)

        val result = useCase(cipher)

        assertTrue(result.isSuccess())
        verify { session.startSession(aesKey) }
    }
}