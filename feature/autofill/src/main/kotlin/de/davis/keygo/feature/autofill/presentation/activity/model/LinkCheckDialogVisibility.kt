package de.davis.keygo.feature.autofill.presentation.activity.model

internal sealed interface LinkCheckDialogVisibility {
    data class Visible(val website: String) : LinkCheckDialogVisibility

    data object Hidden : LinkCheckDialogVisibility
}
