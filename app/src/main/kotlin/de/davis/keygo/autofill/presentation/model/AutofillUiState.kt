package de.davis.keygo.autofill.presentation.model

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.ItemIdNone

data class AutofillUiState(
    val request: Request<*> = Request.None,
    val associationDialogVisibility: AssociationDialogVisibility = AssociationDialogVisibility.Hidden,
    val suspicionDialogVisibility: SuspicionDialogVisibility = SuspicionDialogVisibility.Hidden,
    val vaultId: ItemId = ItemIdNone
)