package de.davis.keygo.core.biometrics.data

import androidx.fragment.app.FragmentActivity
import de.davis.keygo.core.biometrics.FakeBiometricAvailabilityRepository
import de.davis.keygo.core.biometrics.domain.model.BiometricAuthError
import de.davis.keygo.core.security.crypto.FakeKeyStoreManager
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.security.domain.model.KeyStoreManagerError
import de.davis.keygo.core.util.assertFailure
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BiometricCryptoImplTest {

    private val availability = FakeBiometricAvailabilityRepository().apply { isAvailable = true }
    private val keyStoreManager = FakeKeyStoreManager()

    private val crypto = BiometricCryptoImpl(
        context = RuntimeEnvironment.getApplication(),
        biometricAvailabilityRepository = availability,
        keyStoreManager = keyStoreManager,
    )

    private fun resumedActivity() = Robolectric.buildActivity(FragmentActivity::class.java).setup()

    private suspend fun unwrap() = crypto.requestUnwrap(
        keyId = KeyId.BiometricVaultKek,
        cryptographicData = CryptographicData(data = ByteArray(48), iv = ByteArray(12)),
    )

    private suspend fun wrap() = crypto.requestWrap(
        keyId = KeyId.BiometricVaultKek,
        key = ByteArray(32) { 1 },
    )

    @Test
    fun `without a resumed activity there is nothing to show the prompt on`() = runTest {
        assertEquals(BiometricAuthError.NoPromptHost, unwrap().assertFailure())
        assertEquals(BiometricAuthError.NoPromptHost, wrap().assertFailure())
    }

    @Test
    fun `a destroyed activity is not used to host the prompt`() = runTest {
        resumedActivity().pause().stop().destroy()

        assertEquals(BiometricAuthError.NoPromptHost, unwrap().assertFailure())
    }

    @Test
    fun `a finishing activity is not used to host the prompt`() = runTest {
        resumedActivity().get().finish()

        assertEquals(BiometricAuthError.NoPromptHost, unwrap().assertFailure())
    }

    @Test
    fun `unusable biometrics are reported before the keystore is asked for a cipher`() = runTest {
        resumedActivity()
        availability.isAvailable = false

        assertEquals(BiometricAuthError.BiometricsNotAvailable, unwrap().assertFailure())
        assertTrue(keyStoreManager.keys.isEmpty())
    }

    @Test
    fun `a permanently invalidated key is reported without showing the prompt`() = runTest {
        resumedActivity()
        keyStoreManager.failure = KeyStoreManagerError.KeyInvalidated

        assertEquals(BiometricAuthError.KeyInvalidated, unwrap().assertFailure())
        assertEquals(BiometricAuthError.KeyInvalidated, wrap().assertFailure())
    }

    @Test
    fun `a keystore failure that names nothing stays retryable`() = runTest {
        resumedActivity()
        keyStoreManager.failure = KeyStoreManagerError.Unknown

        assertEquals(BiometricAuthError.CryptoFailed, unwrap().assertFailure())
    }

    @Test
    fun `a key gated on an unlocked device stays retryable`() = runTest {
        resumedActivity()
        keyStoreManager.deviceLocked = true

        assertEquals(BiometricAuthError.CryptoFailed, wrap().assertFailure())
    }
}
