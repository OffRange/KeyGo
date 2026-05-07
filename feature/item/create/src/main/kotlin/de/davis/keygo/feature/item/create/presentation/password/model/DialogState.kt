package de.davis.keygo.feature.item.create.presentation.password.model

import de.davis.keygo.core.item.domain.model.lite.LiteLogin

sealed interface DialogState {
    data object None : DialogState
    data object TotpParseError : DialogState
    data class SelectItemForModification(val items: List<LiteLogin>) : DialogState
    data class OverrideTotp(val fields: Set<OverrideTotpField>) : DialogState
}