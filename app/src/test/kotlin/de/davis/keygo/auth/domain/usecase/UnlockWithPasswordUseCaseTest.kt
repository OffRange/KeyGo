package de.davis.keygo.auth.domain.usecase

import de.davis.keygo.auth.domain.factory.CipherFactory
import de.davis.keygo.auth.domain.model.CryptographyError
import de.davis.keygo.auth.domain.model.PasswordWrappedKeyData
import de.davis.keygo.auth.domain.repository.DeviceInfoRepository
import de.davis.keygo.auth.domain.repository.KeyDerivationRepository
import de.davis.keygo.auth.domain.repository.PasswordWrappedKeyRepository
import de.davis.keygo.core.domain.Result
import de.davis.keygo.core.domain.Session
import de.davis.keygo.core.domain.isFailure
import de.davis.keygo.core.domain.isSuccess
import de.davis.keygo.core.domain.model.crypto.asAesKey
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import javax.crypto.Cipher
import javax.crypto.SecretKey
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnlockWithPasswordUseCaseTest {

    private lateinit var cipherFactory: CipherFactory
    private lateinit var wrappedKeyRepository: PasswordWrappedKeyRepository
    private lateinit var keyDerivationRepository: KeyDerivationRepository
    private lateinit var deviceInfoRepository: DeviceInfoRepository
    private lateinit var session: Session

    private lateinit var useCase: UnlockWithPasswordUseCase

    @BeforeTest
    fun setUp() {
        cipherFactory = mockk()
        wrappedKeyRepository = mockk()
        keyDerivationRepository = mockk()
        deviceInfoRepository = mockk {
            every { getNumCors() } returns 4
        }
        session = mockk(relaxed = true)

        useCase = UnlockWithPasswordUseCase(
            cipherFactory = cipherFactory,
            wrappedKeyRepository = wrappedKeyRepository,
            keyDerivationRepository = keyDerivationRepository,
            deviceInfoRepository = deviceInfoRepository,
            session = session
        )
    }

    @Test
    fun `test fail when has no wrapped key`() = runTest {
        coEvery { wrappedKeyRepository.getWrappedKeyData() } returns null
        val result = useCase("password")

        assertTrue(result.isFailure())
        assertEquals(expected = CryptographyError.WrappedKeyNotFound, actual = result.error)
    }

    @Test
    fun `test fail when deriving key fails`() = runTest {
        val password = "password"
        val wrappedKeyData = PasswordWrappedKeyData(
            wrappedKey = byteArrayOf(0x1),
            iv = byteArrayOf(0x2),
            salt = byteArrayOf(0x3)
        )
        coEvery { wrappedKeyRepository.getWrappedKeyData() } returns wrappedKeyData

        val failure = CryptographyError.DerivationFailed("<failure message>")
        val parallelism = deviceInfoRepository.getNumCors()
        coEvery {
            keyDerivationRepository.deriveKey(
                password = password.toByteArray(),
                salt = wrappedKeyData.salt,
                parallelism = parallelism
            )
        } returns Result.Failure(
            failure
        )

        val result = useCase(password)

        assertTrue(result.isFailure())
        assertEquals(expected = failure, actual = result.error)
    }

    @Test
    fun `test fail when prepare Cipher fails`() = runTest {
        val password = "password"
        val wrappedKeyData = PasswordWrappedKeyData(
            wrappedKey = byteArrayOf(0x1),
            iv = byteArrayOf(0x2),
            salt = byteArrayOf(0x3)
        )
        coEvery { wrappedKeyRepository.getWrappedKeyData() } returns wrappedKeyData

        val parallelism = deviceInfoRepository.getNumCors()
        coEvery {
            keyDerivationRepository.deriveKey(
                password = password.toByteArray(),
                salt = wrappedKeyData.salt,
                parallelism = parallelism
            )
        } returns Result.Success(byteArrayOf(0x1))

        val failure = CryptographyError.InvalidKey
        every { cipherFactory.prepareCipher(any(), any(), any()) } returns Result.Failure(failure)

        val result = useCase(password)

        assertTrue(result.isFailure())
        assertEquals(expected = failure, actual = result.error)
    }

    @Test
    fun `test fail when unwrapping key fails`() = runTest {
        val password = "password"
        val wrappedKeyData = PasswordWrappedKeyData(
            wrappedKey = byteArrayOf(0x1),
            iv = byteArrayOf(0x2),
            salt = byteArrayOf(0x3)
        )
        coEvery { wrappedKeyRepository.getWrappedKeyData() } returns wrappedKeyData
        val parallelism = deviceInfoRepository.getNumCors()
        coEvery {
            keyDerivationRepository.deriveKey(
                password = password.toByteArray(),
                salt = wrappedKeyData.salt,
                parallelism = parallelism
            )
        } returns Result.Success(byteArrayOf(0x1))

        val cipher = mockk<Cipher>()
        every { cipherFactory.prepareCipher(any(), any(), any()) } returns Result.Success(cipher)

        val error = mockk<CryptographyError>()
        coEvery {
            cipherFactory.unwrapDataKey(
                cipher,
                wrappedKeyData.wrappedKey
            )
        } returns Result.Failure(error)

        val result = useCase(password)

        assertTrue(result.isFailure())
        assertEquals(expected = error, actual = result.error)
    }


    @Test
    fun `test succeeds`() = runTest {
        val password = "password"
        val wrappedKeyData = PasswordWrappedKeyData(
            wrappedKey = byteArrayOf(0x1),
            iv = byteArrayOf(0x2),
            salt = byteArrayOf(0x3)
        )
        coEvery { wrappedKeyRepository.getWrappedKeyData() } returns wrappedKeyData
        coEvery {
            keyDerivationRepository.deriveKey(
                password = password.toByteArray(),
                salt = wrappedKeyData.salt,
                parallelism = 4
            )
        } returns Result.Success(byteArrayOf(0x1))

        val cipher = mockk<Cipher>()
        every { cipherFactory.prepareCipher(any(), any(), any()) } returns Result.Success(cipher)

        val aesKey = mockk<SecretKey>().asAesKey()
        coEvery {
            cipherFactory.unwrapDataKey(
                cipher,
                wrappedKeyData.wrappedKey
            )
        } returns Result.Success(aesKey)

        val result = useCase(password)

        assertTrue(result.isSuccess())
        verify { session.startSession(aesKey) }
    }
}