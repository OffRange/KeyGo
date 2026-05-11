package de.davis.keygo.rust

import de.davisalessandro.keygo.rust.Algorithm
import de.davisalessandro.keygo.rust.TotpServiceInterface

class FakeTotpService : TotpServiceInterface {

    var totpResult: String = "123456"

    override fun getTotp(
        algorithm: Algorithm,
        digits: UByte,
        step: ULong,
        secret: String
    ): String = totpResult

    override fun getTotpFromUrl(url: String): String = totpResult
}
