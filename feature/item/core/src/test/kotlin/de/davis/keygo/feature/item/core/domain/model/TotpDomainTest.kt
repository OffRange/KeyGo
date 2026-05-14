package de.davis.keygo.feature.item.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TotpDomainTest {

    @Test
    fun `issuer present returns issuer`() {
        assertEquals("GitHub", resolveTotpDomain(issuer = "GitHub", accountName = "alice"))
    }

    @Test
    fun `issuer present with email accountName still returns issuer`() {
        assertEquals(
            "Google",
            resolveTotpDomain(issuer = "Google", accountName = "alice@gmail.com"),
        )
    }

    @Test
    fun `null issuer with email accountName returns email domain`() {
        assertEquals(
            "github.com",
            resolveTotpDomain(issuer = null, accountName = "alice@github.com"),
        )
    }

    @Test
    fun `null issuer with bare accountName returns null`() {
        assertNull(resolveTotpDomain(issuer = null, accountName = "alice"))
    }

    @Test
    fun `null issuer with empty accountName returns null`() {
        assertNull(resolveTotpDomain(issuer = null, accountName = ""))
    }

    @Test
    fun `null issuer with email that has empty domain part returns null`() {
        assertNull(resolveTotpDomain(issuer = null, accountName = "alice@"))
    }

    @Test
    fun `empty string issuer falls back to email domain`() {
        assertEquals(
            "example.com",
            resolveTotpDomain(issuer = "", accountName = "alice@example.com"),
        )
    }

    @Test
    fun `empty string issuer with bare accountName returns null`() {
        assertNull(resolveTotpDomain(issuer = "", accountName = "alice"))
    }
}
