package de.davis.keygo.item.create.presentation.password.model

import de.davis.keygo.core.item.domain.model.lite.LitePassword
import kotlinx.collections.immutable.ImmutableList

sealed interface DialogState {
    data object None : DialogState
    data object TotpParseError : DialogState
    data class SelectItemForModification(val items: ImmutableList<LitePassword>) : DialogState
    data class OverrideTotp(val fields: ImmutableList<OverrideTotpField>) : DialogState
}