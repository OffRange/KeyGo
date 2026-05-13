package de.davis.keygo.feature.item.view.login.model

import androidx.compose.runtime.Stable
import de.davis.keygo.feature.totp.domain.model.TotpError
import de.davis.keygo.feature.totp.domain.model.TotpValue

@Stable
sealed interface TotpState {
    data object NoTotp : TotpState
    data class Error(val error: TotpError) : TotpState
    data class HasTotp(val value: TotpValue) : TotpState
}
