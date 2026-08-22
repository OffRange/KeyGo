package de.davis.keygo.feature.item.core.domain.usecase

import de.davis.keygo.core.util.isSuccess
import de.davis.keygo.rust.totp.TotpService
import de.davis.keygo.rust.totp.getInfoFromUriWithResult
import de.davis.keygo.rust.totp.isValidSecret
import org.koin.core.annotation.Single

@Single
class ValidateTotpInputUseCase(
    private val totpService: TotpService,
) {

    operator fun invoke(uriOrSecret: String): Boolean =
        totpService.getInfoFromUriWithResult(uriOrSecret).isSuccess()
                || totpService.isValidSecret(uriOrSecret)
}
