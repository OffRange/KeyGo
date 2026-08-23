package de.davis.keygo.feature.totp.presentation

/**
 * What the redirect knows about the code its deep link carried.
 *
 * The parse is a single synchronous call, so [Validating] is what the screen holds for at most one
 * frame. It exists so the screen never has to read a missing verdict as either "not yet" or
 * "unreadable".
 */
internal sealed interface TotpImportRedirectState {

    data object Validating : TotpImportRedirectState

    data object Valid : TotpImportRedirectState

    data object Invalid : TotpImportRedirectState
}
