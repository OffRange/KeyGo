package de.davis.keygo.core.identity.domain.usecase

import de.davis.keygo.core.biometrics.FakeBiometricAvailabilityRepository
import de.davis.keygo.core.identity.FakeAccountRepository
import de.davis.keygo.core.identity.domain.model.Account
import de.davis.keygo.core.identity.domain.model.BiometricWrappedArk
import de.davis.keygo.core.identity.domain.model.PasswordWrappedArk
import de.davis.keygo.core.identity.domain.model.UnlockableByBiometricsResult
import de.davis.keygo.core.identity.domain.model.hasHardware
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnlockableByBiometricsUseCaseTest {

    private val accountRepository = FakeAccountRepository()
    private val availability = FakeBiometricAvailabilityRepository()

    private val unlockableByBiometrics = UnlockableByBiometricsUseCase(
        accountRepository = accountRepository,
        biometricAvailabilityRepository = availability,
    )

    private fun seedAccount(enrolled: Boolean) = accountRepository.seed(
        Account(
            id = UUID.randomUUID(),
            displayName = "Test",
            passwordWrappedArk = PasswordWrappedArk(
                key = byteArrayOf(1),
                keyIV = byteArrayOf(2),
                salt = byteArrayOf(3),
            ),
            biometricWrappedArk = if (enrolled) BiometricWrappedArk(
                key = byteArrayOf(4),
                keyIV = byteArrayOf(5),
            ) else null,
        )
    )

    @Test
    fun `an enrolled account on usable hardware is Available`() = runTest {
        availability.isAvailable = true
        seedAccount(enrolled = true)

        assertEquals(UnlockableByBiometricsResult.Available, unlockableByBiometrics())
    }

    @Test
    fun `unusable hardware wins over an enrolled account`() = runTest {
        availability.isAvailable = false
        seedAccount(enrolled = true)

        assertEquals(UnlockableByBiometricsResult.NoHardware, unlockableByBiometrics())
    }

    @Test
    fun `usable hardware without an account is NoAccount`() = runTest {
        availability.isAvailable = true

        assertEquals(
            UnlockableByBiometricsResult.NoAccount(hardwareAvailable = true),
            unlockableByBiometrics(),
        )
    }

    @Test
    fun `a missing account is reported even when the hardware is unusable`() = runTest {
        availability.isAvailable = false

        assertEquals(
            UnlockableByBiometricsResult.NoAccount(hardwareAvailable = false),
            unlockableByBiometrics(),
        )
    }

    @Test
    fun `an account that never enrolled is NotEnrolled`() = runTest {
        availability.isAvailable = true
        seedAccount(enrolled = false)

        assertEquals(UnlockableByBiometricsResult.NotEnrolled, unlockableByBiometrics())
    }

    @Test
    fun `NoHardware and a NoAccount without hardware lack hardware`() {
        assertFalse(UnlockableByBiometricsResult.NoHardware.hasHardware())
        assertFalse(UnlockableByBiometricsResult.NoAccount(hardwareAvailable = false).hasHardware())
        assertTrue(UnlockableByBiometricsResult.Available.hasHardware())
        assertTrue(UnlockableByBiometricsResult.NoAccount(hardwareAvailable = true).hasHardware())
        assertTrue(UnlockableByBiometricsResult.NotEnrolled.hasHardware())
    }
}
