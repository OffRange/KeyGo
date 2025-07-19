package de.davis.keygo.item.create.presentation.password.model

import de.davis.keygo.core.domain.model.Password
import kotlinx.collections.immutable.ImmutableList

sealed interface DialogState {
    data object None : DialogState
    data class SelectItemForModification(val items: ImmutableList<Password>) : DialogState
    data class OverrideTotp(val fields: ImmutableList<OverrideTotpField>) : DialogState
}