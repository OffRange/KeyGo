package de.davis.keygo.autofill.presentation.model

sealed interface SuspicionDialogVisibility {
    data class Visible(val appPackageName: String, val website: String) : SuspicionDialogVisibility
    data object Hidden : SuspicionDialogVisibility
}