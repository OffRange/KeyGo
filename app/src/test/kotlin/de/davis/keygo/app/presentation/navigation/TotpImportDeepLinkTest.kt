package de.davis.keygo.app.presentation.navigation

import androidx.core.net.toUri
import androidx.navigation3.runtime.deeplink.DeepLinkRequest
import de.davis.keygo.feature.totp.presentation.TotpImportDeepLinkMatcher
import de.davis.keygo.feature.totp.presentation.TotpImportRedirect
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** An `otpauth://` link has to reach the import gate whole: label and query parameters intact. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TotpImportDeepLinkTest {

    private fun match(uri: String): TotpImportRedirect? =
        TotpImportDeepLinkMatcher.match(DeepLinkRequest(uri.toUri()))?.key

    @Test
    fun `a single query parameter round trips`() {
        val key = assertNotNull(match("otpauth://totp/Example:me@example.com?secret=ABC"))

        assertEquals("otpauth://totp/Example:me@example.com?secret=ABC", key.uri)
    }

    @Test
    fun `every query parameter survives, not just the ones we know about`() {
        val key = assertNotNull(match(DEEP_LINK_URI))

        assertEquals(DEEP_LINK_URI, key.uri)
    }

    /**
     * The label is carried exactly as it arrived, because the parser this is handed to decodes
     * each half of it once itself. Decoding here as well would decode it twice.
     */
    @Test
    fun `a percent encoded label is passed on still encoded`() {
        val key = assertNotNull(match("otpauth://totp/GitHub%3Ame%40github.com?secret=ABC"))

        assertEquals("otpauth://totp/GitHub%3Ame%40github.com?secret=ABC", key.uri)
    }

    /**
     * Pins the bug: reading the decoded label put a real "#" back into the uri, which cut the
     * query off as a fragment and left the import with no secret at all.
     */
    @Test
    fun `an escaped delimiter stays escaped instead of becoming a real one`() {
        val key = assertNotNull(match("otpauth://totp/Acme%23EU:me@acme.com?secret=ABC"))

        assertEquals("otpauth://totp/Acme%23EU:me@acme.com?secret=ABC", key.uri)
    }

    @Test
    fun `an escaped ampersand in a query value does not split the query`() {
        val key = assertNotNull(match("otpauth://totp/Example?secret=ABC&issuer=A%26B"))

        assertEquals("otpauth://totp/Example?secret=ABC&issuer=A%26B", key.uri)
    }

    /** A bare "+" is a space to the parser, so one that arrived escaped has to stay escaped. */
    @Test
    fun `an escaped plus in a query value does not become a space`() {
        val key = assertNotNull(match("otpauth://totp/Example?secret=ABC&issuer=A%2BB"))

        assertEquals("otpauth://totp/Example?secret=ABC&issuer=A%2BB", key.uri)
    }

    @Test
    fun `a link with no query carries nothing to import`() {
        val key = assertNotNull(match("otpauth://totp/Example:me@example.com"))

        assertNull(key.uri)
    }

    @Test
    fun `a link with no label carries nothing to import`() {
        val key = assertNotNull(match("otpauth://totp?secret=ABC"))

        assertNull(key.uri)
    }

    @Test
    fun `the scheme and host are matched without regard to case`() {
        val key = assertNotNull(match("OTPAUTH://TOTP/Example?secret=ABC"))

        assertEquals("otpauth://totp/Example?secret=ABC", key.uri)
    }

    @Test
    fun `links that are not ours do not match`() {
        assertNull(match("https://example.com/totp/Example?secret=ABC"))
        assertNull(match("otpauth://hotp/Example?secret=ABC&counter=1"))
    }

    @Test
    fun `the manifest's intent filter and the parser agree`() {
        assertEquals("otpauth", TotpImportDeepLinkMatcher.SCHEME)
        assertEquals("totp", TotpImportDeepLinkMatcher.HOST)
    }

    private companion object {
        const val DEEP_LINK_URI =
            "otpauth://totp/GitHub:me@github.com?secret=JBSWY3DPEHPK3PXP&issuer=github.com"
    }
}
