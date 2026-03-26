package de.davis.keygo.feature.autofill.presentation.activity.model

import de.davis.keygo.core.item.domain.alias.ItemId

internal sealed interface AutofillUiEvent {
    data object OnAuthenticated : AutofillUiEvent
    data class OnItemSelected(val itemId: ItemId) : AutofillUiEvent
    data object OnAssociate : AutofillUiEvent
    data object OnCancelAssociation : AutofillUiEvent

    data object OnContinueInSuspicion : AutofillUiEvent
    data object OnAbortInSuspicion : AutofillUiEvent

    data object OnDismissGeneratePassword : AutofillUiEvent
    data class OnGeneratedPassword(val password: String) : AutofillUiEvent
}