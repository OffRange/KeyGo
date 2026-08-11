package de.davis.keygo.core.ui.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PendingTotpImportTest {

    @Test
    fun `uri rebuilds the full otpauth string when both parts are present`() {
        val pending = PendingTotpImport(
            totpInfo = "Example:me@example.com",
            queries = "secret=JBSWY3DPEHPK3PXP&issuer=Example",
        )
        assertEquals(
            "otpauth://totp/Example:me@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example",
            pending.uri,
        )
    }

    @Test
    fun `uri is null when totpInfo is missing`() {
        val pending = PendingTotpImport(totpInfo = null, queries = "secret=ABC")
        assertNull(pending.uri)
    }

    @Test
    fun `uri is null when queries is missing`() {
        val pending = PendingTotpImport(totpInfo = "Example:me@example.com", queries = null)
        assertNull(pending.uri)
    }

    @Test
    fun `uri is null when totpInfo is blank`() {
        val pending = PendingTotpImport(totpInfo = "  ", queries = "secret=ABC")
        assertNull(pending.uri)
    }

    @Test
    fun `uri is null when queries is blank`() {
        val pending = PendingTotpImport(totpInfo = "Example:me@example.com", queries = " ")
        assertNull(pending.uri)
    }

    @Test
    fun `default construction has no pending uri`() {
        assertNull(PendingTotpImport().uri)
    }
}
