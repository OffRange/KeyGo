package de.davis.keygo.autofill.presentation.model

import kotlinx.collections.immutable.ImmutableSet

sealed interface AssociationDialogVisibility {
    data object Hidden : AssociationDialogVisibility
    data class Visible(
        val itemName: String,
        val domains: ImmutableSet<String>
    ) : AssociationDialogVisibility
}