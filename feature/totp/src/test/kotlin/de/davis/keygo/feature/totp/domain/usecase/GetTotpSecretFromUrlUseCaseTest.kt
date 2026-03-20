package de.davis.keygo.feature.totp.domain.usecase

import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.totp.domain.model.Algorithm
import de.davis.keygo.feature.totp.domain.model.TotpSecretUrlParseError
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetTotpSecretFromUrlUseCaseTest {
    private lateinit var useCase: GetTotpSecretFromUrlUseCase

    @Before
    fun setUp() {
        useCase = GetTotpSecretFromUrlUseCase()
    }

    @Test
    fun `valid totp url returns totp secret information`() {
        val url =
            "otpauth://totp/Example:alice@google.com?secret=JBSWY3DPEHPK3PXP&issuer=Example&algorithm=SHA1&digits=6&period=30"
        val result = useCase(url)
        assertTrue(result is Result.Success)
        val info = result.success
        assertEquals("JBSWY3DPEHPK3PXP", info.secret)
        assertEquals("Example", info.issuer)
        assertEquals("alice@google.com", info.accountName)
        assertEquals(Algorithm.SHA1, info.algorithm)
        assertEquals(6, info.digits)
        assertEquals(30, info.period)
    }

    @Test
    fun `valid space-contained totp url returns totp secret information`() {
        val url = "otpauth://totp/example.com (alice@gmail.com)?secret=JBSWY3DPEHPK3PXP"
        val result = useCase(url)
        assertTrue(result is Result.Success)
        val info = result.success
        assertEquals("JBSWY3DPEHPK3PXP", info.secret)
        //TODO: extract issue and acc name from a string that is not separated by a ":"
        // --> assertEquals("example.com", info.issuer)
        assertEquals("example.com (alice@gmail.com)", info.accountName)
        assertEquals(Algorithm.SHA1, info.algorithm)
        assertEquals(6, info.digits)
        assertEquals(30, info.period)
    }

    @Test
    fun `missing secret returns no secret provided error`() {
        val url = "otpauth://totp/Example:alice@google.com?issuer=Example"
        val result = useCase(url)
        assertTrue(result is Result.Failure)
        assertTrue(result.error is TotpSecretUrlParseError.NoSecretProvided)
    }

    @Test
    fun `invalid scheme returns scheme not supported error`() {
        val url = "http://totp/Example:alice@google.com?secret=JBSWY3DPEHPK3PXP"
        val result = useCase(url)
        assertTrue(result is Result.Failure)
        val error = result.error
        assertTrue(error is TotpSecretUrlParseError.SchemeNotSupported)
    }

    @Test
    fun `invalid host returns host not supported error`() {
        val url = "otpauth://hotp/Example:alice@google.com?secret=JBSWY3DPEHPK3PXP"
        val result = useCase(url)
        assertTrue(result is Result.Failure)
        val error = result.error
        assertTrue(error is TotpSecretUrlParseError.HostNotSupported)
    }

    @Test
    fun `missing path returns no path provided error`() {
        val url = "otpauth://totp?secret=JBSWY3DPEHPK3PXP"
        val result = useCase(url)
        println(result)
        assertTrue(result is Result.Failure)
        assertTrue(result.error is TotpSecretUrlParseError.NoPathProvided)
    }

    @Test
    fun `missing query returns no query provided error`() {
        val url = "otpauth://totp/Example:alice@google.com"
        val result = useCase(url)
        assertTrue(result is Result.Failure)
        assertTrue(result.error is TotpSecretUrlParseError.NoQueryProvided)
    }

    @Test
    fun `issuer mismatch returns issuer mismatch error`() {
        val url = "otpauth://totp/Issuer1:alice@google.com?secret=JBSWY3DPEHPK3PXP&issuer=Issuer2"
        val result = useCase(url)
        assertTrue(result is Result.Failure)
        val error = result.error
        assertTrue(error is TotpSecretUrlParseError.IssuerMismatch)
    }

    @Test
    fun `missing algorithm digits period uses defaults`() {
        val url = "otpauth://totp/Example:alice@google.com?secret=JBSWY3DPEHPK3PXP&issuer=Example"
        val result = useCase(url)
        assertTrue(result is Result.Success)
        val info = result.success
        assertEquals(Algorithm.SHA1, info.algorithm)
        assertEquals(6, info.digits)
        assertEquals(30, info.period)
    }

    @Test
    fun `path without issuer sets issuer from query`() {
        val url = "otpauth://totp/alice@google.com?secret=JBSWY3DPEHPK3PXP&issuer=Example"
        val result = useCase(url)
        assertTrue(result is Result.Success)
        val info = result.success
        assertEquals("Example", info.issuer)
        assertEquals("alice@google.com", info.accountName)
    }

    @Test
    fun `issuer in path only succeeds`() {
        val url = "otpauth://totp/Example:alice@google.com?secret=JBSWY3DPEHPK3PXP"
        val result = useCase(url)
        assertTrue(result is Result.Success)
        val info = result.success
        assertEquals("Example", info.issuer)
        assertEquals("alice@google.com", info.accountName)
    }
}