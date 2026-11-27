package de.davis.keygo.feature.account.presentation.models

internal enum class LogoutDialogState {
    Hidden,
    Confirming,
    LoggingOut,
    Failed,
}
