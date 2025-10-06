package de.davis.keygo.autofill.presentation.model

sealed interface AssociationDialogVisibility {
    data object Hidden : AssociationDialogVisibility
    data class Visible(
        val itemName: String,
        val domain: String?
    ) : AssociationDialogVisibility
}