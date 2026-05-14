package de.davis.keygo.rust

import de.davisalessandro.keygo.rust.Algorithm
import de.davisalessandro.keygo.rust.TotpInfo
import de.davisalessandro.keygo.rust.TotpServiceInterface

class FakeTotpService : TotpServiceInterface {

    var totpResult: String = "123456"
    var urlResult: String = ""
    var infoFromUriResult: TotpInfo? = null

    override fun getTotp(
        algorithm: Algorithm,
        digits: Int,
        step: Int,
        secret: String
    ): String = totpResult

    override fun getUrl(
        algorithm: Algorithm,
        digits: Int,
        step: Int,
        secret: String,
        issuer: String?,
        accountName: String
    ): String = urlResult

    override fun getInfoFromUri(uri: String): TotpInfo =
        infoFromUriResult ?: error("FakeTotpService.infoFromUriResult not set")
}
