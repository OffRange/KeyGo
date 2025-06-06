package de.davis.keygo.auth.domain.usecase

import de.davis.keygo.auth.data.repository.BiometricWrappedKeyRepository
import de.davis.keygo.auth.domain.model.BiometricAvailability
import de.davis.keygo.auth.domain.repository.BiometricKekRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetBiometricAvailabilityUseCaseTest {

    private lateinit var biometricKekRepository: BiometricKekRepository
    private lateinit var wrappedKeyRepository: BiometricWrappedKeyRepository
    private lateinit var useCase: GetBiometricCryptoSetupAvailabilityUseCase

    @BeforeTest
    fun setUp() {
        biometricKekRepository = mockk()
        wrappedKeyRepository = mockk()
        useCase = GetBiometricCryptoSetupAvailabilityUseCase(
            biometricKekRepository = biometricKekRepository,
            wrappedKeyRepository = wrappedKeyRepository,
        )
    }

    @Test
    fun `test unavailable when has no kek`() = runTest {
        every { biometricKekRepository.hasKek() } returns false
        val result = useCase()

        assertEquals(result, BiometricAvailability.Unavailable(BiometricAvailability.Reason.NoKek))
    }

    @Test
    fun `test unavailable when wrapped key is invalid`() = runTest {
        every { biometricKekRepository.hasKek() } returns true
        coEvery { wrappedKeyRepository.getWrappedKeyData() } returns null
        val result = useCase()

        assertEquals(
            result,
            BiometricAvailability.Unavailable(BiometricAvailability.Reason.InvalidWrappedKey)
        )
    }

    @Test
    fun `test available when all conditions are met`() = runTest {
        every { biometricKekRepository.hasKek() } returns true
        coEvery { wrappedKeyRepository.getWrappedKeyData() } returns mockk {
            every { isValid() } returns true
        }

        val result = useCase()

        assertEquals(result, BiometricAvailability.Available)
    }
}