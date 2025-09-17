package de.davis.keygo.autofill.presentation.model

import de.davis.keygo.core.item.domain.alias.ItemId

sealed interface AutofillUiEvent {
    data object OnAuthenticated : AutofillUiEvent
    data class OnItemSelected(val itemId: ItemId) : AutofillUiEvent
    data object OnAssociate : AutofillUiEvent
    data object OnCancelAssociation : AutofillUiEvent
}