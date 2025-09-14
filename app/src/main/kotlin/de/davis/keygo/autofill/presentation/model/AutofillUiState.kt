package de.davis.keygo.autofill.presentation.model

import de.davis.keygo.core.domain.alias.ItemId
import de.davis.keygo.core.domain.alias.ItemIdNone

data class AutofillUiState(
    val request: Request<*> = Request.None,
    val showAssociationDialog: Boolean = false,
    val vaultId: ItemId = ItemIdNone
)