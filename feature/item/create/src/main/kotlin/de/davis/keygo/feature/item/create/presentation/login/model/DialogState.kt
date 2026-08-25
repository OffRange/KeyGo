package de.davis.keygo.feature.item.create.presentation.login.model

import de.davis.keygo.core.item.domain.model.PasskeyRef

sealed interface DialogState {
    data object None : DialogState
    data object TotpParseError : DialogState
    data class OverrideTotp(val fields: Set<OverrideTotpField>) : DialogState

    data class DeletePasskey(val passkey: PasskeyRef) : DialogState
}
