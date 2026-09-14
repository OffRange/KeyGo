package de.davis.keygo.core.biometrics.data

import de.davis.keygo.core.biometrics.domain.model.BiometricPolicy
import de.davis.keygo.core.biometrics.domain.model.BiometricString
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BiometricStringResolverTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `the default policy asks to authenticate and offers to cancel`() {
        assertEquals("Authenticate", BiometricPolicy.Default.title.resolve(context))
        assertEquals("Cancel", BiometricPolicy.Default.negativeButton.resolve(context))
    }

    @Test
    fun `unlocking an item names the item`() {
        assertEquals("Unlock GitHub", BiometricString.Title.UnlockItem("GitHub").resolve(context))
    }

    @Test
    fun `the password fallback button says so`() {
        assertEquals("Use Password", BiometricString.NegativeButton.Password.resolve(context))
    }
}
