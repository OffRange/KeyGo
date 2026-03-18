package de.davis.keygo.core.identity.domain.usecase

import de.davis.keygo.core.identity.domain.model.PasswordWrappedKeyData
import de.davis.keygo.core.identity.domain.model.UnlockError
import de.davis.keygo.core.identity.domain.repository.DeviceInfoRepository
import de.davis.keygo.core.identity.domain.repository.KeyDerivationRepository
import de.davis.keygo.core.identity.domain.repository.WrappedKeyRepository
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.crypto.model.AesKey
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.isSuccess
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnlockWithPasswordUseCaseTest {

    private val session = mockk<Session>(relaxed = true)
    private val wrappedKeyRepository = mockk<WrappedKeyRepository>()
    private val keyDerivationRepository = mockk<KeyDerivationRepository>()
    private val deviceInfoRepository = mockk<DeviceInfoRepository>()

    private val useCase = UnlockWithPasswordUseCase(
        session = session,
        wrappedKeyRepository = wrappedKeyRepository,
        keyDerivationRepository = keyDerivationRepository,
        deviceInfoRepository = deviceInfoRepository
    )

    private fun generateWrappedKeyData(password: String): Pair<PasswordWrappedKeyData, ByteArray> {
        val dek = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val kekBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val kek = SecretKeySpec(kekBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.WRAP_MODE, kek)
        val wrappedDek = cipher.wrap(dek)
        val iv = cipher.iv

        return PasswordWrappedKeyData(
            key = wrappedDek,
            keyIV = iv,
            salt = salt
        ) to kekBytes
    }

    @Test
    fun `returns WrappedKeyNotFound when repository has no key`() = runTest {
        coEvery { wrappedKeyRepository.getPasswordWrappedKey() } returns Result.Failure(Unit)

        val result = useCase("password")

        assertTrue(result.isFailure())
        assertEquals(UnlockError.WrappedKeyNotFound, (result as Result.Failure).error)
    }

    @Test
    fun `returns DerivationFailed when key derivation fails`() = runTest {
        val (wrappedData, _) = generateWrappedKeyData("password")
        coEvery { wrappedKeyRepository.getPasswordWrappedKey() } returns Result.Success(wrappedData)
        coEvery {
            keyDerivationRepository.deriveKey(
                password = any(),
                salt = any(),
                parallelism = any()
            )
        } returns Result.Failure("derivation error")
        every { deviceInfoRepository.getNumCors() } returns 4

        val result = useCase("password")

        assertTrue(result.isFailure())
        assertEquals(UnlockError.DerivationFailed, (result as Result.Failure).error)
    }

    @Test
    fun `returns UnwrappingFailed when wrong password is used`() = runTest {
        val (wrappedData, _) = generateWrappedKeyData("password")
        val wrongKekBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }

        coEvery { wrappedKeyRepository.getPasswordWrappedKey() } returns Result.Success(wrappedData)
        coEvery {
            keyDerivationRepository.deriveKey(
                password = any(),
                salt = any(),
                parallelism = any()
            )
        } returns Result.Success(wrongKekBytes)
        every { deviceInfoRepository.getNumCors() } returns 4

        val result = useCase("wrong-password")

        assertTrue(result.isFailure())
        assertEquals(UnlockError.UnwrappingFailed, (result as Result.Failure).error)
    }

    @Test
    fun `returns Success and starts session with correct password`() = runTest {
        val (wrappedData, kekBytes) = generateWrappedKeyData("password")

        coEvery { wrappedKeyRepository.getPasswordWrappedKey() } returns Result.Success(wrappedData)
        coEvery {
            keyDerivationRepository.deriveKey(
                password = any(),
                salt = any(),
                parallelism = any()
            )
        } returns Result.Success(kekBytes)
        every { deviceInfoRepository.getNumCors() } returns 4

        val result = useCase("password")

        assertTrue(result.isSuccess())
        val dekSlot = slot<AesKey>()
        verify { session.startSession(capture(dekSlot)) }
        assertTrue(dekSlot.captured.key.encoded.size == 32)
    }

    @Test
    fun `passes correct salt to key derivation`() = runTest {
        val (wrappedData, kekBytes) = generateWrappedKeyData("password")

        coEvery { wrappedKeyRepository.getPasswordWrappedKey() } returns Result.Success(wrappedData)
        coEvery {
            keyDerivationRepository.deriveKey(
                password = any(),
                salt = any(),
                parallelism = any()
            )
        } returns Result.Success(kekBytes)
        every { deviceInfoRepository.getNumCors() } returns 4

        useCase("password")

        val saltSlot = slot<ByteArray>()
        coVerify {
            keyDerivationRepository.deriveKey(
                password = any(),
                salt = capture(saltSlot),
                parallelism = any()
            )
        }
        assertTrue(saltSlot.captured.contentEquals(wrappedData.salt))
    }

    @Test
    fun `passes device core count to key derivation`() = runTest {
        val (wrappedData, kekBytes) = generateWrappedKeyData("password")

        coEvery { wrappedKeyRepository.getPasswordWrappedKey() } returns Result.Success(wrappedData)
        coEvery {
            keyDerivationRepository.deriveKey(
                password = any(),
                salt = any(),
                parallelism = any()
            )
        } returns Result.Success(kekBytes)
        every { deviceInfoRepository.getNumCors() } returns 8

        useCase("password")

        coVerify {
            keyDerivationRepository.deriveKey(
                password = any(),
                salt = any(),
                parallelism = 8
            )
        }
    }
}
