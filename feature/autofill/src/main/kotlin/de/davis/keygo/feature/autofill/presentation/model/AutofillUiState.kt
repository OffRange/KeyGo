package de.davis.keygo.feature.autofill.presentation.model

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.ItemIdNone
import de.davis.keygo.feature.autofill.presentation.activity.model.AssociationDialogVisibility
import de.davis.keygo.feature.autofill.presentation.activity.model.SuspicionDialogVisibility

internal data class AutofillUiState(
    val request: Request<*> = Request.None,
    val associationDialogVisibility: AssociationDialogVisibility = AssociationDialogVisibility.Hidden,
    val suspicionDialogVisibility: SuspicionDialogVisibility = SuspicionDialogVisibility.Hidden,
    val showGeneratePassword: Boolean = false,
    val vaultId: ItemId = ItemIdNone
)