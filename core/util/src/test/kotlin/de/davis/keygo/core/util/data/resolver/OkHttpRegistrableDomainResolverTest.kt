package de.davis.keygo.core.util.data.resolver

import java.net.IDN
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class OkHttpRegistrableDomainResolverTest {

    private val resolver = OkHttpRegistrableDomainResolver()

    @Test
    fun `resolves valid domain to registrable domain`() {
        val domain = "www.example.co.uk"
        val expected = "example.co.uk"

        val result = resolver.resolve(domain)
        assertEquals(expected = expected, actual = result)
    }

    @Test
    fun `resolves domain with scheme to registrable domain`() {
        val domain = "https://www.example.com"
        val expected = "example.com"
        val result = resolver.resolve(domain)

        assertEquals(expected = expected, actual = result)
    }

    @Test
    fun `resolves subdomain to registrable domain`() {
        val domain = "sub.example.com"
        val expected = "example.com"
        val result = resolver.resolve(domain)

        assertEquals(expected = expected, actual = result)
    }

    @Test
    fun `resolves email address to registrable domain`() {
        val domain = "test@example.com"
        val expected = "example.com"
        val result = resolver.resolve(domain)

        assertEquals(expected = expected, actual = result)
    }

    @Test
    fun `resolves email address with subdomain to registrable domain`() {
        val domain = "test@sub.example.com"
        val expected = "example.com"
        val result = resolver.resolve(domain)

        assertEquals(expected = expected, actual = result)
    }

    @Test
    fun `resolves domain with port to registrable domain`() {
        val domain = "example.com:8080"
        val expected = "example.com"
        val result = resolver.resolve(domain)

        assertEquals(expected = expected, actual = result)
    }

    @Test
    fun `resolves domain with paths to registrable domain`() {
        val domain = "example.com/path/to/resource"
        val expected = "example.com"
        val result = resolver.resolve(domain)

        assertEquals(expected = expected, actual = result)
    }

    @Test
    fun `resolves TLD to null`() {
        val domain = "com"
        val expected = null
        val result = resolver.resolve(domain)

        assertEquals(expected = expected, actual = result)
    }

    @Test
    fun `resolves invalid domain to null`() {
        val domain = "invalid domain"
        val expected = null
        val result = resolver.resolve(domain)

        assertEquals(expected = expected, actual = result)
    }

    @Test
    fun `resolves empty string to null`() {
        val domain = ""
        val expected = null
        val result = resolver.resolve(domain)

        assertEquals(expected = expected, actual = result)
    }

    @Test
    fun `resolves blank string to null`() {
        val domain = "   "
        val expected = null
        val result = resolver.resolve(domain)

        assertEquals(expected = expected, actual = result)
    }

    @Test
    fun `resolves scheme to null`() {
        val domain = "https://"
        val expected = null
        val result = resolver.resolve(domain)

        assertEquals(expected = expected, actual = result)
    }

    @Test
    fun `resolves @ to null`() {
        val domain = "@"
        val expected = null
        val result = resolver.resolve(domain)

        assertEquals(expected = expected, actual = result)
    }

    @Test
    fun `resolves email with missing domain to null`() {
        val domain = "test@"
        val expected = null
        val result = resolver.resolve(domain)

        assertEquals(expected = expected, actual = result)
    }

    @Test
    fun `resolves IPv4 to null`() {
        val domain = "127.0.0.1"
        val expected = null
        val result = resolver.resolve(domain)

        assertEquals(expected = expected, actual = result)
    }

    @Test
    fun `resolves padded uppercase domain to registrable domain`() {
        val domain = "   hTTps://WWW.Example.COM/   "
        val expected = "example.com"
        val result = resolver.resolve(domain)

        assertEquals(expected = expected, actual = result)
    }

    @Test
    fun `resolves idn to registrable domain`() {
        val domain = "österreich.at"
        val expected = arrayOf(IDN.toASCII(domain), IDN.toUnicode(domain))
        val result = resolver.resolve(domain)

        assertContains(array = expected, element = result)
    }
}