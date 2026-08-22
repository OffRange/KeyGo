package de.davis.keygo.feature.item.core.domain.usecase

import de.davis.keygo.rust.FakeTotpService
import de.davisalessandro.keygo.rust.Algorithm
import de.davisalessandro.keygo.rust.TotpInfo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidateTotpInputUseCaseTest {

    private val parsedUri = TotpInfo(
        secret = SECRET,
        issuer = "GitHub",
        accountName = "alice@github.com",
        algorithm = Algorithm.SHA1,
        digits = 6,
        period = 30,
    )

    @Test
    fun `accepts an otpauth uri the parser understands`() {
        val validate = makeUseCase(FakeTotpService().apply { infoFromUriResult = parsedUri })

        assertTrue(validate(URI))
    }

    @Test
    fun `accepts a bare secret codes can be generated from`() {
        // No infoFromUriResult, so the fake parser rejects the input and only the secret probe
        // can carry it.
        val validate = makeUseCase(FakeTotpService())

        assertTrue(validate(SECRET))
    }

    @Test
    fun `rejects a bare secret codes cannot be generated from`() {
        val validate = makeUseCase(
            FakeTotpService().apply { invalidSecrets = setOf(NOT_BASE32) },
        )

        assertFalse(validate(NOT_BASE32))
    }

    @Test
    fun `rejects input that is neither a parsable uri nor a usable secret`() {
        val malformedUri = "otpauth://totp/GitHub:alice@github.com?secret=$NOT_BASE32"
        val validate = makeUseCase(
            FakeTotpService().apply { invalidSecrets = setOf(malformedUri) },
        )

        assertFalse(validate(malformedUri))
    }

    @Test
    fun `falls back to the secret probe when the uri parser rejects the input`() {
        // A URI that parses is never handed to the probe, so a service that rejects every secret
        // still validates it.
        val validate = makeUseCase(
            FakeTotpService().apply {
                infoFromUriResult = parsedUri
                invalidSecrets = setOf(URI)
            },
        )

        assertTrue(validate(URI))
    }

    private fun makeUseCase(totpService: FakeTotpService) =
        ValidateTotpInputUseCase(totpService = totpService)

    companion object {
        private const val SECRET = "JBSWY3DPEHPK3PXP"
        private const val NOT_BASE32 = "not base32!"
        private const val URI = "otpauth://totp/GitHub:alice@github.com?secret=$SECRET"
    }
}
