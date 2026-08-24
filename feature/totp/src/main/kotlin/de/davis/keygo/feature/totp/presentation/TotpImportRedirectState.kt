package de.davis.keygo.feature.totp.presentation

internal sealed interface TotpImportRedirectState {

    data object Validating : TotpImportRedirectState

    data object Valid : TotpImportRedirectState

    data object Invalid : TotpImportRedirectState
}
