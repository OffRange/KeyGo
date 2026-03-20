package de.davis.keygo.feature.autofill.presentation.model

internal sealed interface AssociationDialogVisibility {
    data object Hidden : AssociationDialogVisibility
    data class Visible(
        val itemName: String,
        val domain: String?
    ) : AssociationDialogVisibility
}