package de.davis.keygo.feature.totp.presentation

import de.davis.keygo.rust.FakeTotpService
import de.davisalessandro.keygo.rust.Algorithm
import de.davisalessandro.keygo.rust.TotpInfo
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TotpImportRedirectViewModelTest {

    private val totpService = FakeTotpService()

    @Test
    fun `a readable code is valid, and carries the uri that parsed`() {
        totpService.infoFromUriResult = totpInfo()

        val viewModel = buildViewModel(TotpImportRedirect(DEEP_LINK_URI))

        assertEquals(TotpImportRedirectState.Valid(DEEP_LINK_URI), viewModel.state.value)
    }

    @Test
    fun `an unreadable code is invalid`() {
        totpService.infoFromUriResult = null

        val viewModel = buildViewModel(TotpImportRedirect(DEEP_LINK_URI))

        assertEquals(TotpImportRedirectState.Invalid, viewModel.state.value)
    }

    /** The matcher leaves the uri null for a link missing its label or its query. */
    @Test
    fun `a link that carried no complete uri is invalid`() {
        totpService.infoFromUriResult = totpInfo()

        val viewModel = buildViewModel(TotpImportRedirect())

        assertEquals(TotpImportRedirectState.Invalid, viewModel.state.value)
    }

    // Helpers

    private fun buildViewModel(route: TotpImportRedirect) = TotpImportRedirectViewModel(
        route = route,
        totpService = totpService,
    )

    private fun totpInfo() = TotpInfo(
        secret = "JBSWY3DPEHPK3PXP",
        issuer = "github.com",
        accountName = "me@github.com",
        algorithm = Algorithm.SHA1,
        digits = 6,
        period = 30,
    )

    companion object {
        private const val DEEP_LINK_URI =
            "otpauth://totp/GitHub:me@github.com?secret=JBSWY3DPEHPK3PXP&issuer=github.com"
    }
}
