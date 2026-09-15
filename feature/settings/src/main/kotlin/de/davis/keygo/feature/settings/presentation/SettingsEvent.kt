package de.davis.keygo.feature.settings.presentation

internal sealed interface SettingsEvent {

    data object NavigateToLibraries : SettingsEvent
    data object NavigateToChangePassword : SettingsEvent
    data object OpenAutofillSelection : SettingsEvent
    data object ReportIssue : SettingsEvent

    data object NavigateToBackup : SettingsEvent
}