package de.davis.keygo.feature.autofill.presentation.activity.model

internal sealed interface SuspicionDialogVisibility {
    data class Visible(
        val appPackageName: String,
        val website: String,
        val reason: SuspicionReason,
    ) : SuspicionDialogVisibility

    data object Hidden : SuspicionDialogVisibility
}
