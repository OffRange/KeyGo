package de.davis.keygo.feature.totp.presentation

import de.davis.keygo.core.ui.model.PendingTotpImport
import de.davis.keygo.rust.FakeTotpService
import de.davisalessandro.keygo.rust.Algorithm
import de.davisalessandro.keygo.rust.TotpInfo
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the gate a deep-linked code passes before the user is asked to authenticate. A code that
 * cannot be read has to be rejected here, because everything downstream now assumes it was.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TotpImportRedirectViewModelTest {

    private val totpService = FakeTotpService()

    @Test
    fun `a readable code is valid`() {
        totpService.infoFromUriResult = totpInfo()

        val viewModel = buildViewModel(PendingTotpImport(TOTP_INFO, QUERIES))

        assertEquals(TotpImportRedirectState.Valid, viewModel.state.value)
    }

    @Test
    fun `an unreadable code is invalid`() {
        totpService.infoFromUriResult = null

        val viewModel = buildViewModel(PendingTotpImport(TOTP_INFO, QUERIES))

        assertEquals(TotpImportRedirectState.Invalid, viewModel.state.value)
    }

    @Test
    fun `a link with no query string is invalid`() {
        totpService.infoFromUriResult = totpInfo()

        val viewModel = buildViewModel(PendingTotpImport(totpInfo = TOTP_INFO, queries = null))

        assertEquals(TotpImportRedirectState.Invalid, viewModel.state.value)
    }

    @Test
    fun `a link with no path is invalid`() {
        totpService.infoFromUriResult = totpInfo()

        val viewModel = buildViewModel(PendingTotpImport(totpInfo = null, queries = QUERIES))

        assertEquals(TotpImportRedirectState.Invalid, viewModel.state.value)
    }

    // Helpers

    private fun buildViewModel(pendingImport: PendingTotpImport) = TotpImportRedirectViewModel(
        pendingImport = pendingImport,
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
        private const val TOTP_INFO = "GitHub:me@github.com"
        private const val QUERIES = "secret=JBSWY3DPEHPK3PXP&issuer=github.com"
    }
}
