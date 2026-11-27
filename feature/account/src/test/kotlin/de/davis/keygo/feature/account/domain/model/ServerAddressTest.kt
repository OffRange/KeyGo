package de.davis.keygo.feature.account.domain.model

import de.davis.keygo.core.util.Result
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerAddressTest {

    private fun origin(input: String) = (ServerAddress.parse(input) as? Result.Success)?.success?.origin

    private fun error(input: String) = (ServerAddress.parse(input) as? Result.Failure)?.error

    @Test
    fun bare_host_defaults_to_https() {
        assertEquals("https://vault.example.org", origin("vault.example.org"))
    }

    @Test
    fun keeps_an_explicit_scheme_and_port() {
        assertEquals("http://localhost:8000", origin("http://localhost:8000"))
        assertEquals("https://192.168.178.21:8000", origin("https://192.168.178.21:8000"))
        assertEquals("https://[::1]:8443", origin("[::1]:8443"))
    }

    @Test
    fun drops_the_default_port_of_its_scheme() {
        assertEquals("https://vault.example.org", origin("vault.example.org:443"))
        assertEquals("http://localhost", origin("http://localhost:80"))
        assertEquals("https://vault.example.org:80", origin("vault.example.org:80"))
        assertEquals("http://localhost:443", origin("http://localhost:443"))
    }

    @Test
    fun normalises_an_origin() {
        assertEquals("https://vault.example.org", origin("  HTTPS://Vault.Example.org "))
        assertEquals("https://vault.example.org", origin("vault.example.org/"))
    }

    /** Dropping it would connect to the domain's root, not the server the user meant. */
    @Test
    fun rejects_anything_after_the_origin() {
        listOf(
            "vault.example.org/keygo",
            "https://vault.example.org/keygo/",
            "vault.example.org?x=1",
            "vault.example.org/?x=1",
            "vault.example.org#top",
            "vault.example.org#",
            "  HTTPS://Vault.Example.org/login?x=1#top ",
        ).forEach { assertEquals(ServerAddressError.NotAnOrigin, error(it), "for $it") }
    }

    @Test
    fun blank_input_is_empty() {
        assertEquals(ServerAddressError.Empty, error(""))
        assertEquals(ServerAddressError.Empty, error("   "))
    }

    @Test
    fun rejects_what_no_server_can_be() {
        listOf(
            "ftp://vault.example.org",
            "https://",
            "vault example.org",
            "vault.example.org:99999",
            "user:pass@vault.example.org",
        ).forEach { assertEquals(ServerAddressError.Invalid, error(it), "for $it") }
    }
}
