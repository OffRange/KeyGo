package de.davis.keygo.feature.autofill.presentation.model

internal sealed interface SuspicionDialogVisibility {
    data class Visible(val appPackageName: String, val website: String) : SuspicionDialogVisibility
    data object Hidden : SuspicionDialogVisibility
}