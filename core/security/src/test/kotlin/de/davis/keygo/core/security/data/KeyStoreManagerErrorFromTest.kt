package de.davis.keygo.core.security.data

import de.davis.keygo.core.security.domain.model.KeyStoreManagerError
import java.security.InvalidKeyException
import javax.crypto.AEADBadTagException
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The invalidated-key branch is reachable from a plain JVM test through [AEADBadTagException],
 * which is what a key that no longer matches its ciphertext surfaces as. The
 * KeyPermanentlyInvalidatedException and UserNotAuthenticatedException branches are Android
 * framework types that cannot be constructed here, so what is pinned for them is the shape they
 * share with everything else: the classifier walks a bounded cause chain and falls back to
 * [KeyStoreManagerError.Unknown] rather than guessing.
 */
class KeyStoreManagerErrorFromTest {

    @Test
    fun `a failed tag reports the key as invalidated`() {
        assertEquals(
            KeyStoreManagerError.KeyInvalidated,
            keyStoreManagerErrorFrom(AEADBadTagException()),
        )
    }

    @Test
    fun `a failed tag is recognised through the wrapper the keystore throws`() {
        // What Cipher.unwrap actually raises: the tag failure arrives as a cause.
        val thrown = InvalidKeyException("Failed to unwrap key", AEADBadTagException())

        assertEquals(KeyStoreManagerError.KeyInvalidated, keyStoreManagerErrorFrom(thrown))
    }

    @Test
    fun `an unrecognised failure is left unnamed`() {
        assertEquals(
            KeyStoreManagerError.Unknown,
            keyStoreManagerErrorFrom(InvalidKeyException("keystore busy")),
        )
    }

    @Test
    fun `a self referencing cause chain terminates`() {
        val looping = object : RuntimeException("loops") {
            override val cause: Throwable get() = this
        }

        assertEquals(KeyStoreManagerError.Unknown, keyStoreManagerErrorFrom(looping))
    }

    @Test
    fun `a tag failure at the edge of the walk is still found`() {
        assertEquals(
            KeyStoreManagerError.KeyInvalidated,
            keyStoreManagerErrorFrom(tagFailureBuriedUnder(wrappers = 7)),
        )
    }

    @Test
    fun `a tag failure past the end of the walk is not chased`() {
        assertEquals(
            KeyStoreManagerError.Unknown,
            keyStoreManagerErrorFrom(tagFailureBuriedUnder(wrappers = 8)),
        )
    }

    /** An [AEADBadTagException] wrapped [wrappers] deep, returned as the outermost throwable. */
    private fun tagFailureBuriedUnder(wrappers: Int): Throwable {
        var thrown: Throwable = AEADBadTagException()
        repeat(wrappers) { thrown = InvalidKeyException("wrapper", thrown) }
        return thrown
    }
}
