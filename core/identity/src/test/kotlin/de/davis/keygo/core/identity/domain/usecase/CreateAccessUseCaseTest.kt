package de.davis.keygo.core.identity.domain.usecase

import de.davis.keygo.core.identity.domain.model.BiometricWrappedKeyData
import de.davis.keygo.core.identity.domain.model.CreateAccessError
import de.davis.keygo.core.identity.domain.model.PasswordWrappedKeyData
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
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreateAccessUseCaseTest {

    private val session = mockk<Session>(relaxed = true)
    private val wrappedKeyRepository = mockk<WrappedKeyRepository>(relaxed = true)
    private val keyDerivationRepository = mockk<KeyDerivationRepository>()
    private val deviceInfoRepository = mockk<DeviceInfoRepository>()

    private val useCase = CreateAccessUseCase(
        keyDerivationRepository = keyDerivationRepository,
        deviceInfoRepository = deviceInfoRepository,
        wrappedKeyRepository = wrappedKeyRepository,
        session = session
    )

    private fun setupSuccessfulDerivation() {
        val kekBytes = ByteArray(32) { it.toByte() }
        coEvery {
            keyDerivationRepository.deriveKey(
                password = any(),
                salt = any(),
                parallelism = any()
            )
        } returns Result.Success(kekBytes)
        every { deviceInfoRepository.getNumCors() } returns 4
    }

    @Test
    fun `returns KeyDerivationFailed when derivation fails`() = runTest {
        coEvery {
            keyDerivationRepository.deriveKey(
                password = any(),
                salt = any(),
                parallelism = any()
            )
        } returns Result.Failure("error")
        every { deviceInfoRepository.getNumCors() } returns 4

        val result = useCase("password")

        assertTrue(result.isFailure())
        assertEquals(CreateAccessError.KeyDerivationFailed, result.error)
    }

    @Test
    fun `returns Success and starts session without biometric cipher`() = runTest {
        setupSuccessfulDerivation()

        val result = useCase("password", biometricCipher = null)

        assertTrue(result.isSuccess())
        val dekSlot = slot<AesKey>()
        verify { session.startSession(capture(dekSlot)) }
        assertEquals(32, dekSlot.captured.key.encoded.size)
    }

    @Test
    fun `stores password wrapped key on success`() = runTest {
        setupSuccessfulDerivation()

        useCase("password")

        val pwSlot = slot<PasswordWrappedKeyData>()
        coVerify { wrappedKeyRepository.setPasswordWrappedKey(capture(pwSlot)) }
        assertTrue(pwSlot.captured.key.isNotEmpty())
        assertTrue(pwSlot.captured.keyIV.isNotEmpty())
        assertTrue(pwSlot.captured.salt.isNotEmpty())
    }

    @Test
    fun `salt is 16 bytes`() = runTest {
        setupSuccessfulDerivation()

        useCase("password")

        val pwSlot = slot<PasswordWrappedKeyData>()
        coVerify { wrappedKeyRepository.setPasswordWrappedKey(capture(pwSlot)) }
        assertEquals(16, pwSlot.captured.salt.size)
    }

    @Test
    fun `stores biometric wrapped key when cipher provided`() = runTest {
        setupSuccessfulDerivation()

        val biometricKek = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val biometricCipher = Cipher.getInstance("AES/GCM/NoPadding")
        biometricCipher.init(Cipher.WRAP_MODE, biometricKek)

        val result = useCase("password", biometricCipher = biometricCipher)

        assertTrue(result.isSuccess())
        val bioSlot = slot<BiometricWrappedKeyData>()
        coVerify { wrappedKeyRepository.setBiometricWrappedKey(capture(bioSlot)) }
        assertTrue(bioSlot.captured.key.isNotEmpty())
        assertTrue(bioSlot.captured.keyIV.isNotEmpty())
    }

    @Test
    fun `does not store biometric key when no cipher provided`() = runTest {
        setupSuccessfulDerivation()

        useCase("password", biometricCipher = null)

        coVerify(exactly = 0) { wrappedKeyRepository.setBiometricWrappedKey(any()) }
    }

    @Test
    fun `passes password bytes to derivation`() = runTest {
        setupSuccessfulDerivation()

        useCase("myPassword123")

        val passwordSlot = slot<ByteArray>()
        coVerify {
            keyDerivationRepository.deriveKey(
                password = capture(passwordSlot),
                salt = any(),
                parallelism = any()
            )
        }
        assertTrue(passwordSlot.captured.contentEquals("myPassword123".toByteArray()))
    }

    @Test
    fun `passes device core count as parallelism`() = runTest {
        val kekBytes = ByteArray(32) { it.toByte() }
        coEvery {
            keyDerivationRepository.deriveKey(
                password = any(),
                salt = any(),
                parallelism = any()
            )
        } returns Result.Success(kekBytes)
        every { deviceInfoRepository.getNumCors() } returns 12

        useCase("password")

        coVerify {
            keyDerivationRepository.deriveKey(
                password = any(),
                salt = any(),
                parallelism = 12
            )
        }
    }

    @Test
    fun `generates different salts for different invocations`() = runTest {
        setupSuccessfulDerivation()

        useCase("password")
        val slot1 = slot<PasswordWrappedKeyData>()
        coVerify { wrappedKeyRepository.setPasswordWrappedKey(capture(slot1)) }
        val salt1 = slot1.captured.salt.clone()

        useCase("password")
        val slot2 = mutableListOf<PasswordWrappedKeyData>()
        coVerify { wrappedKeyRepository.setPasswordWrappedKey(capture(slot2)) }
        val salt2 = slot2.last().salt

        // While not guaranteed by SecureRandom, duplicate 16-byte salts are astronomically unlikely
        assertTrue(!salt1.contentEquals(salt2))
    }
}
