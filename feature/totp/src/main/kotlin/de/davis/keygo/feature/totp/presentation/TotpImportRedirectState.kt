package de.davis.keygo.feature.totp.presentation

internal sealed interface TotpImportRedirectState {

    data object Validating : TotpImportRedirectState

    /** Carries the uri that parsed, so the screen does not have to re-derive it from the route. */
    data class Valid(val uri: String) : TotpImportRedirectState

    data object Invalid : TotpImportRedirectState
}
