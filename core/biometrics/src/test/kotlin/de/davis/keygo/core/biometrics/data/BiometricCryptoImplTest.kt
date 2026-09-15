package de.davis.keygo.core.biometrics.data

import androidx.fragment.app.FragmentActivity
import de.davis.keygo.core.biometrics.FakeBiometricAvailabilityRepository
import de.davis.keygo.core.biometrics.domain.model.BiometricAuthError
import de.davis.keygo.core.security.crypto.FakeKeyStoreManager
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.core.security.domain.model.KeyId
import de.davis.keygo.core.security.domain.model.KeyStoreManagerError
import de.davis.keygo.core.util.assertFailure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
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

    private suspend fun wrap() = crypto.requestWrap(keyId = KeyId.BiometricVaultKek) { seal ->
        seal(ByteArray(32) { 1 })
    }

    private fun FragmentActivity.showsPrompt(): Boolean =
        supportFragmentManager.findFragmentByTag(BIOMETRIC_FRAGMENT_TAG) != null

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

    /**
     * With several activities resumed at once (multi-window on API 29+), one can pause after another
     * has become the host. Clearing the host on any pause would drop the one in front, so every
     * request would fail until it resumed again.
     */
    @Test
    fun `an activity leaving behind the host leaves the host in place`() = runTest {
        val behind = resumedActivity()
        resumedActivity()
        behind.pause().stop().destroy()
        // Reaching the keystore at all means a host was found.
        keyStoreManager.failure = KeyStoreManagerError.KeyInvalidated

        assertEquals(BiometricAuthError.KeyInvalidated, unwrap().assertFailure())
    }

    /**
     * BiometricPrompt drops an authenticate() made after onSaveInstanceState without calling back,
     * so a request that used a stopped host hung with nothing on screen.
     */
    @Test
    fun `a host the user has left is not used to show the prompt`() = runTest {
        val controller = resumedActivity().pause().stop()

        assertEquals(BiometricAuthError.NoPromptHost, unwrap().assertFailure())
        assertFalse(controller.get().showsPrompt())
    }

    /**
     * A paused host is not stopped yet, so its state is not saved either. When the activity covering
     * it belongs to the app, nothing cancels a prompt opened on it, and on API 26-27 that prompt is a
     * dialog in a window the user cannot see.
     */
    @Test
    fun `a paused host is not used to show the prompt`() = runTest {
        val controller = resumedActivity().pause()

        val request = async { unwrap() }
        advanceUntilIdle()

        assertFalse(controller.get().showsPrompt())
        assertEquals(BiometricAuthError.NoPromptHost, request.await().assertFailure())
    }

    /**
     * An activity can start a request before it has resumed, once the one the user left has paused.
     * Failing on the host that is missing in that gap failed the request before its own activity got
     * there.
     */
    @Test
    fun `a request made while its activity starts waits for it past the host left behind`() =
        runTest {
            resumedActivity().pause().stop()
            val starting = Robolectric.buildActivity(FragmentActivity::class.java)
                .create()
                .start()
                .postCreate(null)

            val request = async { unwrap() }
            runCurrent()
            assertFalse(request.isCompleted)

            starting.resume().visible()
            runCurrent()

            assertTrue(starting.get().showsPrompt())
            request.cancel()
        }

    /**
     * Every prompt built on an activity replaces the callback the one before it registered. The
     * failure the second request would fail with shows whether it ran alongside the first.
     */
    @Test
    fun `a second request waits for the prompt already on screen`() = runTest {
        resumedActivity()
        val first = async { unwrap() }
        runCurrent()
        keyStoreManager.failure = KeyStoreManagerError.Unknown

        val second = async { unwrap() }
        runCurrent()
        assertFalse(second.isCompleted)

        first.cancel()
        runCurrent()

        assertEquals(BiometricAuthError.CryptoFailed, second.await().assertFailure())
    }

    @Test
    fun `the key to wrap is not asked for before the user authenticates`() = runTest {
        val controller = resumedActivity()
        var asked = false

        val request = async {
            crypto.requestWrap(keyId = KeyId.BiometricVaultKek) { seal ->
                asked = true
                seal(ByteArray(32))
            }
        }
        runCurrent()

        assertTrue(controller.get().showsPrompt())
        assertFalse(asked)
        request.cancel()
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

    private companion object {
        // BiometricPrompt.BIOMETRIC_FRAGMENT_TAG is package-private.
        const val BIOMETRIC_FRAGMENT_TAG = "androidx.biometric.internal.BiometricFragment"
    }
}
